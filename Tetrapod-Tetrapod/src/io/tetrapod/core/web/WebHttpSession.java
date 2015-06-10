package io.tetrapod.core.web;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.tetrapod.protocol.core.Core.*;
import static io.tetrapod.protocol.core.CoreContract.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.tetrapod.core.*;
import io.tetrapod.core.json.JSONArray;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.core.registry.EntityToken;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;

public class WebHttpSession extends WebSession {
   protected static final Logger               logger            = LoggerFactory.getLogger(WebHttpSession.class);

   public static final Timer                   requestTimes      = Metrics.timer(WebHttpSession.class, "requests", "time");

   private static final int                    FLOOD_TIME_PERIOD = 2000;
   private static final int                    FLOOD_WARN        = 200;
   private static final int                    FLOOD_IGNORE      = 300;
   private static final int                    FLOOD_KILL        = 400;

   private volatile long                       floodPeriod;

   private int                                 reqCounter        = 0;

   private final String                        wsLocation;
   private WebSocketServerHandshaker           handshaker;

   private Map<Integer, ChannelHandlerContext> contexts;

   public WebHttpSession(SocketChannel ch, Session.Helper helper, Map<String, WebRoot> roots, String wsLocation) {
      super(ch, helper);

      this.wsLocation = wsLocation;

      ch.pipeline().addLast("codec-http", new HttpServerCodec());
      ch.pipeline().addLast("aggregator", new HttpObjectAggregator(65536));
      ch.pipeline().addLast("api", this);
      ch.pipeline().addLast("deflater", new HttpContentCompressor(6));
      ch.pipeline().addLast("files", new WebStaticFileHandler(roots));
   }

   @Override
   public void checkHealth() {
      if (isConnected()) {
         timeoutPendingRequests();
      }
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof FullHttpRequest) {
         handleHttpRequest(ctx, (FullHttpRequest) msg);
      } else if (msg instanceof WebSocketFrame) {
         handleWebSocketFrame(ctx, (WebSocketFrame) msg);
      }
   }

   @Override
   public void channelActive(final ChannelHandlerContext ctx) throws Exception {
      fireSessionStartEvent();
      scheduleHealthCheck();
   }

   @Override
   public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      fireSessionStopEvent();
      cancelAllPendingRequests();
   }

   public synchronized boolean isWebSocket() {
      return handshaker != null;
   }

   public synchronized void initLongPoll() {
      if (contexts == null) {
         contexts = new HashMap<>();
      }
   }

   private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
      if (frame instanceof CloseWebSocketFrame) {
         handshaker.close(ctx.channel(), new CloseWebSocketFrame());
         return;
      }
      if (frame instanceof PingWebSocketFrame) {
         ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
         return;
      }
      if (!(frame instanceof TextWebSocketFrame)) {
         throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass().getName()));
      }

      String request = ((TextWebSocketFrame) frame).text();
      ReferenceCountUtil.release(frame);
      try {
         JSONObject jo = new JSONObject(request);
         WebContext webContext = new WebContext(jo);
         RequestHeader header = webContext.makeRequestHeader(this, null);
         readAndDispatchRequest(header, webContext.getRequestParams());
         return;
      } catch (IOException e) {
         logger.error("error processing websocket request", e);
         ctx.channel().writeAndFlush(new TextWebSocketFrame("Illegal request: " + request));
      }
   }

   private void handleHttpRequest(final ChannelHandlerContext ctx, final FullHttpRequest req) throws Exception {
      if (logger.isDebugEnabled()) {
         synchronized (this) {
            logger.debug(String.format("%s REQUEST[%d] = %s : %s", this, ++reqCounter, ctx.channel().remoteAddress(), req.getUri()));
         }
      }
      // see if we need to start a web socket session
      if (wsLocation.equals(req.getUri())) {
         WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(wsLocation, null, false);
         synchronized (this) {
            handshaker = wsFactory.newHandshaker(req);
         }
         if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
         } else {
            handshaker.handshake(ctx.channel(), req);
         }
      } else {
         final Context metricContext = requestTimes.time();
         if (!req.getDecoderResult().isSuccess()) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
         }

         // this handles long-polling calls when we fall back to long-polling for browsers with no websockets
         if (req.getUri().equals("/poll")) {
            handlePoll(ctx, req);
            return;
         }
         // check for web-route handler
         final WebRoute route = relayHandler.getWebRoutes().findRoute(req.getUri());
         if (route != null) {
            handleWebRoute(ctx, req, route);
         } else {
            // pass request down the pipeline
            ctx.fireChannelRead(req);
         }
         metricContext.stop();
      }
   }

   private String getContent(final FullHttpRequest req) {
      final ByteBuf contentBuf = req.content();
      try {
         return contentBuf.toString(Charset.forName("UTF-8"));
      } finally {
         contentBuf.release();
      }
   }

   private void handlePoll(final ChannelHandlerContext ctx, final FullHttpRequest req) throws Exception {
      //logger.debug("{} POLLER: {} keepAlive = {}", this, req.getUri(), HttpHeaders.isKeepAlive(req));
      final JSONObject params = new JSONObject(getContent(req));

      // authenticate this session, if needed
      if (params.has("_token")) {
         final EntityToken t = EntityToken.decode(params.getString("_token"));
         if (t != null) {
            if (relayHandler.validate(t.entityId, t.nonce)) {
               setTheirEntityId(t.entityId);
               setTheirEntityType(Core.TYPE_CLIENT);
            } else {
               sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
               return;
            }
         }
      }

      initLongPoll();

      if (params.has("_requestId")) {
         logger.debug("{} RPC: structId={}", this, params.getInt("_structId"));
         // dispatch a request
         final RequestHeader header = WebContext.makeRequestHeader(this, null, params);

         header.fromType = Core.TYPE_CLIENT;
         header.fromId = getTheirEntityId();
         synchronized (contexts) {
            contexts.put(header.requestId, ctx);
         }
         readAndDispatchRequest(header, params);

      } else {
         getDispatcher().dispatch(new Runnable() {
            public void run() {
               final LongPollQueue messages = LongPollQueue.getQueue(getTheirEntityId());
               final long startTime = System.currentTimeMillis();
               // long poll -- wait until there are messages in queue, and return them
               assert messages != null;
               // we grab a lock so only one poll request processes at a time
               longPoll(25, messages, startTime, ctx, req);
            }
         });
      }
   }

   private void longPoll(final int millis, final LongPollQueue messages, final long startTime, final ChannelHandlerContext ctx,
         final FullHttpRequest req) {
      getDispatcher().dispatch(millis, TimeUnit.MILLISECONDS, new Runnable() {
         public void run() {
            if (messages.tryLock()) {
               try {
                  if (messages.size() > 0) {
                     final JSONArray arr = new JSONArray();
                     while (!messages.isEmpty()) {
                        JSONObject jo = messages.poll();
                        if (jo != null) {
                           arr.put(jo);
                        }
                     }
                     logger.debug("{} long poll {} has {} items\n", this, messages.getEntityId(), messages.size());
                     ctx.writeAndFlush(makeFrame(new JSONObject().put("messages", arr), HttpHeaders.isKeepAlive(req)));
                  } else {
                     if (System.currentTimeMillis() - startTime > Util.ONE_SECOND * 10) {
                        ctx.writeAndFlush(makeFrame(new JSONObject().put("messages", new JSONArray()), HttpHeaders.isKeepAlive(req)));
                     } else {
                        // wait a bit and check again
                        longPoll(50, messages, startTime, ctx, req);
                     }
                  }
               } finally {
                  messages.unlock();
               }
            } else {
               ctx.writeAndFlush(makeFrame(new JSONObject().put("error", "locked"), HttpHeaders.isKeepAlive(req)));
            }
         } 
      });
   }

   // handle a JSON API call
   private void handleWebRoute(final ChannelHandlerContext ctx, final FullHttpRequest req, final WebRoute route) throws Exception {
      final WebContext context = new WebContext(req, route.path);
      final RequestHeader header = context.makeRequestHeader(this, route);
      if (header != null) {
         //final long t0 = System.currentTimeMillis();
         logger.debug("{} WEB API REQUEST: {} keepAlive = {}", this, req.getUri(), HttpHeaders.isKeepAlive(req));
         header.requestId = requestCounter.incrementAndGet();
         header.fromType = Core.TYPE_WEBAPI;
         header.fromId = getMyEntityId();

         final ResponseHandler handler = new ResponseHandler() {
            @Override
            public void onResponse(Response res) {
               //logger.info("{} WEB API RESPONSE: {} {} ms", WebHttpSession.this, res, (System.currentTimeMillis() - t0));
               handleWebAPIResponse(ctx, req, res);
            }
         };

         try {
            if (header.structId == WebAPIRequest.STRUCT_ID) {
               // @webapi() generic WebAPIRequest call 
               String body = req.content().toString(CharsetUtil.UTF_8);
               if (body == null || body.trim().isEmpty()) {
                  body = req.getUri();
               }
               final WebAPIRequest request = new WebAPIRequest(route.path, getHeaders(req).toString(), context.getRequestParams()
                     .toString(), body);
               final int toEntityId = relayHandler.getAvailableService(header.contractId);
               if (toEntityId != 0) {
                  final Session ses = relayHandler.getRelaySession(toEntityId, header.contractId);
                  if (ses != null) {
                     header.contractId = Core.CONTRACT_ID;
                     header.toId = toEntityId;
                     //logger.info("{} WEB API REQEUST ROUTING TO {} {}", this, toEntityId, header.dump());
                     relayRequest(header, request, ses, handler);
                  } else {
                     logger.debug("{} Could not find a relay session for {} {}", this, header.toId, header.contractId);
                     handler.fireResponse(new Error(ERROR_SERVICE_UNAVAILABLE), header);
                  }
               } else {
                  logger.debug("{} Could not find a service for {}", this, header.contractId);
                  handler.fireResponse(new Error(ERROR_SERVICE_UNAVAILABLE), header);
               }
            } else {
               // @web() specific Request mapping 
               final Structure request = readRequest(header, context.getRequestParams());
               if (request != null) {
                  relayRequest(header, request, handler);
               } else {
                  handler.fireResponse(new Error(ERROR_UNKNOWN_REQUEST), header);
               }
            }
         } finally {
            ReferenceCountUtil.release(req);
         }

      } else {
         sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
      }
   }

   private void handleWebAPIResponse(final ChannelHandlerContext ctx, final FullHttpRequest req, Response res) {
      final boolean keepAlive = HttpHeaders.isKeepAlive(req);
      try {
         ChannelFuture cf = null;
         if (res.isError()) {
            if (res.errorCode() == CoreContract.ERROR_TIMEOUT) {
               sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, REQUEST_TIMEOUT));
            } else {
               JSONObject jo = new JSONObject();
               jo.put("result", "ERROR");
               jo.put("error", res.errorCode());
               jo.put("message", Contract.getErrorCode(res.errorCode(), res.getContractId()));
               cf = ctx.writeAndFlush(makeFrame(jo, keepAlive));
            }
         } else if (isGenericSuccess(res)) {
            // bad form to allow two types of non-error response but most calls will just want to return SUCCESS
            JSONObject jo = new JSONObject();
            jo.put("result", "SUCCESS");
            cf = ctx.writeAndFlush(makeFrame(jo, keepAlive));
         } else if (res instanceof WebAPIResponse) {
            WebAPIResponse resp = (WebAPIResponse) res;
            if (resp.redirect != null && !resp.redirect.isEmpty()) {
               redirect(resp.redirect, ctx);
            } else {
               cf = ctx.writeAndFlush(makeFrame(new JSONObject(resp.json), keepAlive));
            }
         } else {
            ctx.writeAndFlush(makeFrame(res, 0));
         }
         if (cf != null && !keepAlive) {
            cf.addListener(ChannelFutureListener.CLOSE);
         }
      } catch (Throwable e) {
         logger.error(e.getMessage(), e);
      }
   }

   private JSONObject getHeaders(FullHttpRequest req) {
      JSONObject jo = new JSONObject();
      for (Map.Entry<String, String> e : req.headers()) {
         jo.put(e.getKey(), e.getValue());
      }
      return jo;
   }

   protected void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
      // Generate an error page if response getStatus code is not OK (200).
      if (res.getStatus().code() != 200) {
         ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
         try {
            res.content().writeBytes(buf);
         } finally {
            buf.release();
         }
         setContentLength(res, res.content().readableBytes());
      }
      // Send the response and close the connection if necessary.
      ChannelFuture f = ctx.channel().writeAndFlush(res);
      if (!isKeepAlive(req) || res.getStatus().code() != 200) {
         f.addListener(ChannelFutureListener.CLOSE);
      }
   }

   @Override
   protected Object makeFrame(JSONObject jo, boolean keepAlive) {
      if (isWebSocket()) {
         return new TextWebSocketFrame(jo.toString(3));
      } else {
         if (jo.has("__httpOverride")) {
            ByteBuf buf = WebContext.makeByteBufResult(jo.optString("__httpPayload"));
            FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, buf);
            httpResponse.headers().set(CONTENT_TYPE, jo.optString("__httpMime", "text/json"));
            httpResponse.headers().set(CONTENT_LENGTH, httpResponse.content().readableBytes());
            if (jo.has("__httpDisposition")) {
               httpResponse.headers().set("Content-Disposition", jo.optString("__httpDisposition"));
            }
            if (keepAlive) {
               httpResponse.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
            } else {
               httpResponse.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
            }
            return httpResponse;
         }
         ByteBuf buf = WebContext.makeByteBufResult(jo.toString(3));
         FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, buf);
         httpResponse.headers().set(CONTENT_TYPE, "text/json");
         httpResponse.headers().set(CONTENT_LENGTH, httpResponse.content().readableBytes());
         if (keepAlive) {
            httpResponse.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
         } else {
            httpResponse.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
         }
         // logger.debug("MAKE FRAME " + jo);
         return httpResponse;
      }
   }

   private void relayRequest(RequestHeader header, Structure request, ResponseHandler handler) throws IOException {
      final Session ses = relayHandler.getRelaySession(header.toId, header.contractId);
      if (ses != null) {
         relayRequest(header, request, ses, handler);
      } else {
         logger.debug("{} Could not find a relay session for {} {}", this, header.toId, header.contractId);
         handler.fireResponse(new Error(ERROR_SERVICE_UNAVAILABLE), header);
      }
   }

   protected Async relayRequest(RequestHeader header, Structure request, Session ses, ResponseHandler handler) throws IOException {
      final ByteBuf in = convertToByteBuf(request);
      try {
         return ses.sendRelayedRequest(header, in, this, handler);
      } finally {
         in.release();
      }
   }

   protected ByteBuf convertToByteBuf(Structure struct) throws IOException {
      ByteBuf buffer = channel.alloc().buffer(32);
      ByteBufDataSource data = new ByteBufDataSource(buffer);
      struct.write(data);
      return buffer;
   }

   private void redirect(String newURL, ChannelHandlerContext ctx) {
      HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, FOUND);
      response.headers().set(LOCATION, newURL);
      response.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
      ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
   }

   protected void readAndDispatchRequest(RequestHeader header, JSONObject params) {
      long now = System.currentTimeMillis();
      lastHeardFrom.set(now);

      if (floodCheck(now, header)) {
         // could move flood check after comms log just for the logging
         return;
      }
      try {
         final Structure request = readRequest(header, params);
         if (request != null) {
            if (header.toId == DIRECT || header.toId == myId) {
               if (request instanceof Request) {
                  dispatchRequest(header, (Request) request);
               } else {
                  logger.error("Asked to process a request I can't  deserialize {}", header.dump());
                  sendResponse(new Error(ERROR_SERIALIZATION), header.requestId);
               }
            } else {
               relayRequest(header, request);
            }
         } else {
            sendResponse(new Error(ERROR_UNKNOWN_REQUEST), header.requestId);
         }
      } catch (IOException e) {
         logger.error("Error processing request {}", header.dump());
         sendResponse(new Error(ERROR_UNKNOWN), header.requestId);
      }
   }

   private ChannelHandlerContext getContext(int requestId) {
      synchronized (contexts) {
         return contexts.remove(requestId);
      }
   }

   @Override
   public void sendResponse(Response res, int requestId) {
      if (isWebSocket()) {
         super.sendResponse(res, requestId);
      } else {
         // HACK: for http responses we need to write to the response to the correct ChannelHandlerContext 
         if (res != Response.PENDING) {
            if (!commsLogIgnore(res))
               commsLog("%s  [%d] => %s", this, requestId, res.dump());
            final Object buffer = makeFrame(res, requestId);
            if (buffer != null && channel.isActive()) {
               ChannelHandlerContext ctx = getContext(requestId);
               if (ctx != null) {
                  ctx.writeAndFlush(buffer);
               } else {
                  logger.warn("{} Could not find context for {}", this, requestId);
                  writeFrame(buffer);
               }
            }
         }
      }
   }

   @Override
   public void sendRelayedResponse(ResponseHeader header, ByteBuf payload) {
      if (isWebSocket()) {
         super.sendRelayedResponse(header, payload);
      } else {
         // HACK: for http responses we need to write to the response to the correct ChannelHandlerContext 
         if (!commsLogIgnore(header.structId))
            commsLog("%s  [%d] ~> Response:%d", this, header.requestId, header.structId);
         ChannelHandlerContext ctx = getContext(header.requestId);
         final Object buffer = makeFrame(header, payload, ENVELOPE_RESPONSE);
         if (ctx != null) {
            ctx.writeAndFlush(buffer);
         } else {
            writeFrame(buffer);
         }
      }
   }

   private Async relayRequest(RequestHeader header, Structure request) throws IOException {
      final Session ses = relayHandler.getRelaySession(header.toId, header.contractId);
      if (ses != null) {
         return relayRequest(header, request, ses, null);
      } else {
         logger.debug("Could not find a relay session for {} {}", header.toId, header.contractId);
         sendResponse(new Error(ERROR_SERVICE_UNAVAILABLE), header.requestId);
         return null;
      }
   }

   @Override
   public void sendRelayedMessage(MessageHeader header, ByteBuf payload, boolean broadcast) {
      if (isWebSocket()) {
         super.sendRelayedMessage(header, payload, broadcast);
      } else {
         // queue the message for long poller to retrieve later
         if (!commsLogIgnore(header.structId)) {
            commsLog("%s  [M] ~] Message:%d %s (to %s:%d)", this, header.structId, getNameFor(header), TO_TYPES[header.toType], header.toId);
         }
         final LongPollQueue messages = LongPollQueue.getQueue(getTheirEntityId());
         // FIXME: Need a sensible way to protect against memory gobbling if this queue isn't cleared fast enough
         messages.add(toJSON(header, payload, ENVELOPE_MESSAGE));
         //logger.debug("{} Queued {} messages for longPoller {}", this, messages.size(), messages.getEntityId());
      }
   }

   private boolean floodCheck(long now, RequestHeader header) {
      int reqs;
      long newFloodPeriod = now / FLOOD_TIME_PERIOD;
      if (newFloodPeriod != floodPeriod) {
         // race condition between read and write of floodPeriod.  but is benign as it means we reset
         // request count to 0 an extra time
         floodPeriod = newFloodPeriod;
         requestCount.set(0);
         reqs = 0;
      } else {
         reqs = requestCount.incrementAndGet();
      }
      if (reqs > FLOOD_KILL) {
         logger.warn("{} flood killing {}/{}", this, header.contractId, header.structId);
         close();
         return true;
      }
      if (reqs > FLOOD_IGNORE) {
         // do nothing, send no response
         logger.warn("{} flood ignoring {}/{}", this, header.contractId, header.structId);
         return true;
      }
      if (reqs > FLOOD_WARN) {
         // respond with error so client can slow down
         logger.warn("{} flood warning {}/{}", this, header.contractId, header.structId);
         sendResponse(new Error(ERROR_FLOOD), header.requestId);
         return true;
      }
      return false;
   }
   
   private boolean isGenericSuccess(Response r) {
      Response s = Response.SUCCESS;
      return s.getContractId() == r.getContractId() && s.getStructId() == r.getStructId();
   }

}
