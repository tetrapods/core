package io.tetrapod.core.web;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.tetrapod.protocol.core.Core.DIRECT;
import static io.tetrapod.protocol.core.Core.ENVELOPE_RESPONSE;
import static io.tetrapod.protocol.core.CoreContract.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.tetrapod.core.Contract;
import io.tetrapod.core.Session;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;
import io.tetrapod.protocol.core.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebHttpSession extends WebSession {
   protected static final Logger logger            = LoggerFactory.getLogger(WebHttpSession.class);

   private static final int      FLOOD_TIME_PERIOD = 2000;
   private static final int      FLOOD_WARN        = 200;
   private static final int      FLOOD_IGNORE      = 300;
   private static final int      FLOOD_KILL        = 400;

   private volatile long         floodPeriod;

   public WebHttpSession(SocketChannel ch, Session.Helper helper, Map<String, WebRoot> roots) {
      super(ch, helper);

      ch.pipeline().addLast("codec-http", new HttpServerCodec());
      ch.pipeline().addLast("aggregator", new HttpObjectAggregator(65536));
      ch.pipeline().addLast("api", this);
      ch.pipeline().addLast("deflater", new HttpContentCompressor(6));
      ch.pipeline().addLast("files", new WebStaticFileHandler(roots));
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (!(msg instanceof HttpRequest)) {
         ctx.fireChannelRead(msg);
         return;
      }
      handleHttpRequest(ctx, (FullHttpRequest) msg);
   }

   @Override
   public void checkHealth() {
      if (isConnected()) {
         timeoutPendingRequests();
      }
   }

   private Map<Integer, ChannelHandlerContext> contexts = new HashMap<>();

   protected void handleHttpRequest(final ChannelHandlerContext ctx, final FullHttpRequest req) throws Exception {

      if (!req.getDecoderResult().isSuccess()) {
         sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
         return;
      }

      // this handles long-polling calls when we fall back to long-polling for browsers with no websockets
      if (req.getUri().equals("/rpc")) {
         logger.info("{} POLLER: {} keepAlive = {}", this, req.getUri(), HttpHeaders.isKeepAlive(req));
         final WebContext context = new WebContext(req);
         final RequestHeader header = context.makeRequestHeader(this, null);
         final Structure request = readRequest(header, context);
         if (request != null) {

            final JSONObject params = context.getRequestParams();
            // FIXME: Use auth-cookie instead?
            if (params.has("_token") && params.has("_fromId")) {
               // find & validate entity
               // mark socket as a poller            
               // keep socket alive if we can
               setTheirEntityId(params.getInt("_fromId"));
               setTheirEntityType(Core.TYPE_CLIENT);
               header.fromType = Core.TYPE_CLIENT;
               header.fromId = getMyEntityId();
            }
            synchronized (contexts) {
               contexts.put(header.requestId, ctx);
            }
            readAndDispatchRequest(header, context);
         } else {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
         }
         return;
      }

      final WebRoute route = relayHandler.getWebRoutes().findRoute(req.getUri());
      if (route != null) {
         // handle a JSON API call
         final WebContext context = new WebContext(req);
         final RequestHeader header = context.makeRequestHeader(this, route);
         if (header != null) {
            //final long t0 = System.currentTimeMillis();
            logger.info("{} WEB API REQEUST: {} keepAlive = {}", this, req.getUri(), HttpHeaders.isKeepAlive(req));
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
                  final WebAPIRequest request = new WebAPIRequest(route.path, getHeaders(req).toString(), context.getRequestParams()
                        .toString(), req.content().toString(CharsetUtil.UTF_8));
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
                        handler.onResponse(new Error(ERROR_SERVICE_UNAVAILABLE));
                     }
                  } else {
                     logger.debug("{} Could not find a service for {}", this, header.contractId);
                     handler.onResponse(new Error(ERROR_SERVICE_UNAVAILABLE));
                  }
               } else {
                  // @web() specific Request mapping 
                  final Structure request = readRequest(header, context);
                  if (request != null) {
                     relayRequest(header, request, handler);
                  }
               }
            } finally {
               ReferenceCountUtil.release(req);
            }

         } else {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
         }
      } else {
         ctx.fireChannelRead(req);
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
         } else if (res.isGenericSuccess()) {
            // bad form to allow two types of non-error response but most calls will just want to return SUCCESS
            JSONObject jo = new JSONObject();
            jo.put("result", "SUCCESS");
            cf = ctx.writeAndFlush(makeFrame(jo, keepAlive));
         } else {
            WebAPIResponse resp = (WebAPIResponse) res;
            if (resp.redirect != null && !resp.redirect.isEmpty()) {
               redirect(resp.redirect, ctx);
            } else {
               cf = ctx.writeAndFlush(makeFrame(new JSONObject(resp.json), keepAlive));
            }
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
      ByteBuf buf = WebContext.makeByteBufResult(jo.toString(3));
      FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, buf);
      httpResponse.headers().set(CONTENT_TYPE, "text/json");
      httpResponse.headers().set(CONTENT_LENGTH, httpResponse.content().readableBytes());
      if (keepAlive) {
         httpResponse.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
      } else {
         httpResponse.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
      }
      logger.debug("MAKE FRAME " + jo);
      return httpResponse;
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

   private void relayRequest(RequestHeader header, Structure request, ResponseHandler handler) throws IOException {
      final Session ses = relayHandler.getRelaySession(header.toId, header.contractId);
      if (ses != null) {
         relayRequest(header, request, ses, handler);
      } else {
         logger.debug("Could not find a relay session for {} {}", header.toId, header.contractId);
         handler.onResponse(new Error(ERROR_SERVICE_UNAVAILABLE));
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

   protected void readAndDispatchRequest(RequestHeader header, WebContext context) {
      long now = System.currentTimeMillis();
      lastHeardFrom.set(now);

      if (floodCheck(now, header)) {
         // could move flood check after comms log just for the logging
         return;
      }
      try {
         final Structure request = readRequest(header, context);
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

   @Override
   public void sendRelayedResponse(ResponseHeader header, ByteBuf payload) {
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

   protected Async relayRequest(RequestHeader header, Structure request) throws IOException {
      final Session ses = relayHandler.getRelaySession(header.toId, header.contractId);
      if (ses != null) {
         return relayRequest(header, request, ses, null);
      } else {
         logger.debug("Could not find a relay session for {} {}", header.toId, header.contractId);
         sendResponse(new Error(ERROR_SERVICE_UNAVAILABLE), header.requestId);
         return null;
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
         logger.warn("flood killing {}/{}", header.contractId, header.structId);
         close();
         return true;
      }
      if (reqs > FLOOD_IGNORE) {
         // do nothing, send no response
         logger.warn("flood ignoring {}/{}", header.contractId, header.structId);
         return true;
      }
      if (reqs > FLOOD_WARN) {
         // respond with error so client can slow down
         logger.warn("flood warning {}/{}", header.contractId, header.structId);
         sendResponse(new Error(ERROR_FLOOD), header.requestId);
         return true;
      }
      return false;
   }
}
