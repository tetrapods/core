package io.tetrapod.web;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.tetrapod.protocol.core.Core.*;
import static io.tetrapod.protocol.core.CoreContract.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.slf4j.*;

import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;

import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cors.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.*;
import io.tetrapod.core.*;
import io.tetrapod.core.json.*;
import io.tetrapod.core.logging.CommsLogger;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;
import io.tetrapod.core.tasks.TaskContext;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;

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

   private String                              httpReferrer      = null;
   private String                              domain            = null;
   private String                              build             = null;

   public WebHttpSession(SocketChannel ch, Session.Helper helper, Map<String, WebRoot> roots, String wsLocation) {
      super(ch, helper);

      this.wsLocation = wsLocation;

      ch.pipeline().addLast("codec-http", new HttpServerCodec());
      ch.pipeline().addLast("aggregator", new HttpObjectAggregator(65536));
      ch.pipeline().addLast("cors", new CorsHandler(CorsConfigBuilder.forAnyOrigin().allowedRequestHeaders("Content-Type").build()));
      ch.pipeline().addLast("api", this);
      ch.pipeline().addLast("deflater", new HttpContentCompressor(6));
      ch.pipeline().addLast("maintenance", new MaintenanceHandler(roots.get("tetrapod")));
      ch.pipeline().addLast("files", new WebStaticFileHandler(roots));
   }

   @Override
   public void checkHealth() {
      TaskContext taskContext = TaskContext.pushNew();
      try {
         if (isConnected()) {
            timeoutPendingRequests();
         }
      } finally {
         taskContext.pop();
      }
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      TaskContext taskContext = TaskContext.pushNew();
      try {
         if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
         } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
         }
      } finally {
         taskContext.pop();
      }
   }

   @Override
   public void channelActive(final ChannelHandlerContext ctx) throws Exception {
      TaskContext taskContext = TaskContext.pushNew();
      try {
         fireSessionStartEvent();
         scheduleHealthCheck();
      } finally {
         taskContext.pop();
      }

   }

   @Override
   public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      TaskContext taskContext = TaskContext.pushNew();
      try {
         fireSessionStopEvent();
         cancelAllPendingRequests();
      } finally {
         taskContext.pop();
      }
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
      if (frame instanceof PongWebSocketFrame) {
         return;
      }
      if (frame instanceof ContinuationWebSocketFrame) {
         ctx.channel().write(frame, ctx.voidPromise());
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
      } catch (IOException | JSONException e) {
         logger.error("error processing websocket request", e);
         ctx.channel().writeAndFlush(new TextWebSocketFrame("Illegal request: " + request));
      }
   }

   private void handleHttpRequest(final ChannelHandlerContext ctx, final FullHttpRequest req) throws Exception {
      if (logger.isTraceEnabled()) {
         synchronized (this) {
            logger.trace(String.format("%s REQUEST[%d] = %s : %s", this, ++reqCounter, ctx.channel().remoteAddress(), req.uri()));
         }
      }
      // Set the http referrer for this request, if not already set
      if (Util.isEmpty(getHttpReferrer())) {
         setHttpReferrer(req.headers().get("Referer"));
         logger.debug("•••• Referer: {} ", getHttpReferrer());
      }
      // Set the domain for this request, if not already set
      if (Util.isEmpty(getDomain())) {
         setDomain(req.headers().get("Host"));
         logger.debug("•••• Domain: {} ", getDomain());
      }

      // see if we need to start a web socket session
      if (wsLocation != null && wsLocation.equals(req.uri())) {

         if (Util.isProduction()) {
            String host = req.headers().get("Host");
            String origin = req.headers().get("Origin");
            if (origin != null && host != null) {
               if (!(origin.equals("http://" + host) || origin.equals("https://" + host))) {
                  logger.warn("origin [{}] doesn't match host [{}]", origin, host);
                  sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED));
                  return;
               }
            }
         }

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
         if (!req.decoderResult().isSuccess()) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
         }

         // this handles long-polling calls when we fall back to long-polling
         // for browsers with no websockets
         if (req.uri().equals("/poll")) {
            handlePoll(ctx, req);
            return;
         }
         // check for web-route handler
         final WebRoute route = relayHandler.getWebRoutes().findRoute(req.uri());
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
      logger.debug("{} POLLER: {} keepAlive = {}", this, req.uri(), HttpUtil.isKeepAlive(req));
      final JSONObject params = new JSONObject(getContent(req));

      // authenticate this session, if needed
      if (params.has("_token")) {
         Integer clientId = LongPollToken.validateToken(params.getString("_token"));
         if (clientId != null) {
            setTheirEntityId(clientId);
            setTheirEntityType(Core.TYPE_CLIENT);
            LongPollQueue.getQueue(clientId, true); // init long poll queue
         } else {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
         }
      }

      initLongPoll();

      if (params.has("_requestId")) {
         logger.debug("{} RPC: structId={}", this, params.getInt("_structId"));
         // dispatch a request
         final RequestHeader header = WebContext.makeRequestHeader(this, null, params);

         header.fromType = Core.TYPE_CLIENT;
         header.fromChildId = getTheirEntityId();
         header.fromParentId = getMyEntityId();
         synchronized (contexts) {
            contexts.put(header.requestId, ctx);
         }
         readAndDispatchRequest(header, params);

      } else if (getTheirEntityId() != 0) {
         getDispatcher().dispatch(() -> {
            final LongPollQueue messages = LongPollQueue.getQueue(getTheirEntityId(), false);
            final long startTime = System.currentTimeMillis();
            // long poll -- wait until there are messages in queue, and
            // return them
            assert messages != null;
            // we grab a lock so only one poll request processes at a time
            longPoll(25, messages, startTime, ctx, req);
         });
      }
   }

   private void longPoll(final int millis, final LongPollQueue messages, final long startTime, final ChannelHandlerContext ctx,
         final FullHttpRequest req) {
      getDispatcher().dispatch(millis, TimeUnit.MILLISECONDS, () -> {
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
                  ctx.writeAndFlush(makeFrame(new JSONObject().put("messages", arr), HttpUtil.isKeepAlive(req)));
               } else {
                  if (System.currentTimeMillis() - startTime > Util.ONE_SECOND * 10) {
                     ctx.writeAndFlush(makeFrame(new JSONObject().put("messages", new JSONArray()), HttpUtil.isKeepAlive(req)));
                  } else {
                     // wait a bit and check again
                     longPoll(50, messages, startTime, ctx, req);
                  }
               }
               messages.setLastDrain(System.currentTimeMillis());
            } finally {
               messages.unlock();
            }
         } else {
            ctx.writeAndFlush(makeFrame(new JSONObject().put("error", "locked"), HttpUtil.isKeepAlive(req)));
         }
      });
   }

   // handle a JSON API call
   private void handleWebRoute(final ChannelHandlerContext ctx, final FullHttpRequest req, final WebRoute route) throws Exception {
      final WebContext context = new WebContext(req, route.path);
      final RequestHeader header = context.makeRequestHeader(this, route);
      if (header != null) {
         // final long t0 = System.currentTimeMillis();
         logger.debug("{} WEB API REQUEST: {} keepAlive = {}", this, req.uri(), HttpUtil.isKeepAlive(req));
         header.requestId = requestCounter.incrementAndGet();
         header.fromType = Core.TYPE_WEBAPI;
         header.fromParentId = getMyEntityId();
         header.fromChildId = getTheirEntityId();

         final ResponseHandler handler = new ResponseHandler() {
            @Override
            public void onResponse(Response res) {
               logger.info("{} WEB API RESPONSE: {} ", WebHttpSession.this, res);
               handleWebAPIResponse(ctx, req, res);
            }
         };

         try {
            if (header.structId == WebAPIRequest.STRUCT_ID) {
               // @webapi() generic WebAPIRequest call
               String body = req.content().toString(CharsetUtil.UTF_8);
               if (body == null || body.trim().isEmpty()) {
                  body = req.uri();
               }

               final WebAPIRequest request = new WebAPIRequest(route.path, getHeaders(req).toString(),
                     context.getRequestParams().toString(), body, req.uri());

               final int toEntityId = relayHandler.getAvailableService(header.contractId);
               if (toEntityId != 0) {
                  final Session ses = relayHandler.getRelaySession(toEntityId, header.contractId);
                  if (ses != null) {
                     header.contractId = Core.CONTRACT_ID;
                     header.toId = toEntityId;
                     logger.info("{} WEB API REQUEST ROUTING TO {} {}", this, toEntityId, header.dump());
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

                  if (header.contractId == TetrapodContract.CONTRACT_ID && header.toId == 0) {
                     header.toId = Core.DIRECT;
                  }

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
      final boolean keepAlive = HttpUtil.isKeepAlive(req);
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
            // bad form to allow two types of non-error response but most
            // calls will just want to return SUCCESS
            JSONObject jo = new JSONObject();
            jo.put("result", "SUCCESS");
            cf = ctx.writeAndFlush(makeFrame(jo, keepAlive));
         } else if (res instanceof WebAPIResponse) {
            WebAPIResponse resp = (WebAPIResponse) res;
            if (resp.redirect != null && !resp.redirect.isEmpty()) {
               redirect(resp.redirect, ctx);
            } else {
               if (resp.json != null) {
                  cf = ctx.writeAndFlush(makeFrame(new JSONObject(resp.json), keepAlive));
               } else {
                  logger.warn("{} WebAPIResponse JSON is null: {}", this, resp.dump());
               }
            }
         } else {
            // a blank response
            ctx.writeAndFlush(makeFrame(new ResponseHeader(0, 0, 0, 0), res, ENVELOPE_RESPONSE));
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
      if (res.status().code() != 200) {
         ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
         try {
            res.content().writeBytes(buf);
         } finally {
            buf.release();
         }
         HttpUtil.setContentLength(res, res.content().readableBytes());
      }
      // Send the response and close the connection if necessary.
      ChannelFuture f = ctx.channel().writeAndFlush(res);
      if (!HttpUtil.isKeepAlive(req) || res.status().code() != 200) {
         f.addListener(ChannelFutureListener.CLOSE);
      }
   }

   @Override
   protected Object makeFrame(JSONObject jo, boolean keepAlive) {
      if (isWebSocket()) {
         return new TextWebSocketFrame(jo.toString(3));
      } else {
         String payload = jo.optString("__httpPayload", null);
         if (payload == null) {
            payload = jo.toStringWithout("__http");
         }
         ByteBuf buf = WebContext.makeByteBufResult(payload + '\n');
         HttpResponseStatus status = HttpResponseStatus.valueOf(jo.optInt("__httpStatus", OK.code()));
         FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, status, buf);
         httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, jo.optString("__httpMime", "application/json"));
         httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, httpResponse.content().readableBytes());
         httpResponse.headers().set(HttpHeaderNames.CONNECTION, keepAlive ? HttpHeaderValues.KEEP_ALIVE : HttpHeaderValues.CLOSE);
         if (jo.has("__httpDisposition")) {
            httpResponse.headers().set("Content-Disposition", jo.optString("__httpDisposition"));
         }
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
      response.headers().set(HttpHeaderNames.LOCATION, newURL);
      response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
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
                  sendResponse(new Error(ERROR_SERIALIZATION), header);
               }
            } else {
               relayRequest(header, request);
            }
         } else {
            sendResponse(new Error(ERROR_UNKNOWN_REQUEST), header);
         }
      } catch (IOException e) {
         logger.error("Error processing request {}", header.dump());
         sendResponse(new Error(ERROR_UNKNOWN), header);
      }
   }

   private ChannelHandlerContext getContext(int requestId) {
      synchronized (contexts) {
         return contexts.remove(requestId);
      }
   }

   @Override
   public void sendResponse(Response res, RequestHeader reqHeader) {
      if (isWebSocket()) {
         super.sendResponse(res, reqHeader);
      } else {
         // HACK: for http responses we need to write to the response to the
         // correct ChannelHandlerContext
         if (res != Response.PENDING) {
            final ResponseHeader header = new ResponseHeader(reqHeader.requestId, res.getContractId(), res.getStructId(),
                  reqHeader.contextId);
            CommsLogger.append(this, true, header, res, reqHeader.structId);
            final Object buffer = makeFrame(header, res, ENVELOPE_RESPONSE);
            if (buffer != null && channel.isActive()) {
               ChannelHandlerContext ctx = getContext(reqHeader.requestId);
               if (ctx != null) {
                  ctx.writeAndFlush(buffer);
               } else {
                  logger.warn("{} Could not find context for {}", this, reqHeader.requestId);
                  writeFrame(buffer);
               }
            }
         }
      }
   }

   @Override
   public void sendRelayedResponse(ResponseHeader header, Async async, ByteBuf payload) {
      if (isWebSocket()) {
         super.sendRelayedResponse(header, async, payload);
      } else {
         CommsLogger.append(this, true, header, payload, async.header.structId);
         // HACK: for http responses we need to write to the response to the
         // correct ChannelHandlerContext
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
         sendResponse(new Error(ERROR_SERVICE_UNAVAILABLE), header);
         return null;
      }
   }

   @Override
   public void sendRelayedMessage(MessageHeader header, ByteBuf payload, boolean broadcast) {
      if (isWebSocket()) {
         super.sendRelayedMessage(header, payload, broadcast);
      } else {
         // queue the message for long poller to retrieve later
         CommsLogger.append(this, true, header, payload);
         final LongPollQueue messages = LongPollQueue.getQueue(getTheirEntityId(), false);
         if (messages != null) {
            // FIXME: Need a sensible way to protect against memory gobbling
            // if this queue isn't cleared fast enough
            messages.add(toJSON(header, payload, ENVELOPE_MESSAGE));
            logger.debug("{} Queued {} messages for longPoller {}", this, messages.size(), messages.getEntityId());
         }
      }
   }

   private boolean floodCheck(long now, RequestHeader header) {
      int reqs;
      long newFloodPeriod = now / FLOOD_TIME_PERIOD;
      if (newFloodPeriod != floodPeriod) {
         // race condition between read and write of floodPeriod. but is
         // benign as it means we reset
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
         sendResponse(new Error(ERROR_FLOOD), header);
         return true;
      }
      return false;
   }

   private boolean isGenericSuccess(Response r) {
      Response s = Response.SUCCESS;
      return s.getContractId() == r.getContractId() && s.getStructId() == r.getStructId();
   }

   public synchronized String getHttpReferrer() {
      return httpReferrer;
   }

   public synchronized void setHttpReferrer(String httpReferrer) {
      this.httpReferrer = httpReferrer;
   }

   public synchronized String getDomain() {
      return domain;
   }

   public synchronized void setDomain(String domain) {
      this.domain = domain;
   }

   public synchronized void setBuild(String build) {
      this.build = build;
   }

   public synchronized String getBuild() {
      return build;
   }

}
