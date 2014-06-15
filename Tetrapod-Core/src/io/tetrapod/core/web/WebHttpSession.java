package io.tetrapod.core.web;

import static io.netty.handler.codec.http.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.tetrapod.protocol.core.CoreContract.ERROR_SERVICE_UNAVAILABLE;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.*;
import io.tetrapod.core.*;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;
import io.tetrapod.protocol.core.*;

import java.io.IOException;
import java.util.Map;

import org.slf4j.*;

public class WebHttpSession extends WebSession {
   protected static final Logger logger = LoggerFactory.getLogger(WebHttpSession.class);

   private boolean               isKeepAlive;

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

   protected void handleHttpRequest(final ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
      if (!req.getDecoderResult().isSuccess()) {
         sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
         return;
      }

      final WebRoute route = relayHandler.getWebRoutes().findRoute(req.getUri());
      if (route != null) {
         // handle a JSON API call
         final WebContext context = new WebContext(req);
         final RequestHeader header = context.makeRequestHeader(this, route);
         if (header != null) {
            header.requestId = requestCounter.incrementAndGet();
            header.fromType = Core.TYPE_WEBAPI;
            isKeepAlive = HttpHeaders.isKeepAlive((HttpRequest) req);

            final ResponseHandler handler = new ResponseHandler() {
               @Override
               public void onResponse(Response res) {
                  try {
                     if (res.isError()) {
                        JSONObject jo = new JSONObject();
                        jo.put("result", "ERROR");
                        jo.put("error", res.errorCode());
                        jo.put("message", Contract.getErrorCode(res.errorCode(), res.getContractId()));
                        ctx.writeAndFlush(makeFrame(jo)).addListener(ChannelFutureListener.CLOSE);
                     } else if (res.isGenericSuccess()) {
                        // bad form to allow two types of non-error response but most calls will just want to return SUCCESS
                        JSONObject jo = new JSONObject();
                        jo.put("result", "SUCCESS");
                        ctx.writeAndFlush(makeFrame(jo)).addListener(ChannelFutureListener.CLOSE);
                     } else {
                        WebAPIResponse resp = (WebAPIResponse) res;
                        if (resp.redirect != null) {
                           redirect(resp.redirect, ctx);
                        } else {
                           ctx.writeAndFlush(makeFrame(new JSONObject(resp.json))).addListener(ChannelFutureListener.CLOSE);
                        }
                     }
                  } catch (Throwable e) {
                     logger.error(e.getMessage(), e);
                  }
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
//                      ses.sendRequest(request, header).handle(handler);
                        relayRequest(header, request, ses).handle(handler);
                     } else {
                        logger.debug("Could not find a relay session for {} {}", header.toId, header.contractId);
                        handler.onResponse(new Error(ERROR_SERVICE_UNAVAILABLE));
                     }
                  } else {
                     handler.onResponse(new Error(ERROR_SERVICE_UNAVAILABLE));
                  }
               } else {
                  // @web() specific Request mapping 
                  Structure request = readRequest(header, context);
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
   protected Object makeFrame(JSONObject jo) {
      ByteBuf buf = WebContext.makeByteBufResult(jo.toString(3));
      FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, buf);
      httpResponse.headers().set(CONTENT_TYPE, "text/json");
      httpResponse.headers().set(CONTENT_LENGTH, httpResponse.content().readableBytes());
      if (isKeepAlive) {
         httpResponse.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
      } else {
         httpResponse.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
      }
      return httpResponse;
   }

   @Override
   public void channelActive(final ChannelHandlerContext ctx) throws Exception {
      fireSessionStartEvent();
   }

   @Override
   public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      fireSessionStopEvent();
      cancelAllPendingRequests();
   }

   private  void relayRequest(RequestHeader header, Structure request, ResponseHandler handler) throws IOException {
      final Session ses = relayHandler.getRelaySession(header.toId, header.contractId);
      if (ses != null) {
         relayRequest(header, request, ses).handle(handler);
      } else {
         logger.debug("Could not find a relay session for {} {}", header.toId, header.contractId);
         handler.onResponse(new Error(ERROR_SERVICE_UNAVAILABLE));
      }
   }

   protected Async relayRequest(RequestHeader header, Structure request, Session ses) throws IOException {
      final ByteBuf in = convertToByteBuf(request);
      try {
         return ses.sendRelayedRequest(header, in, this);
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
      response.headers().set(CONNECTION, "close");
      ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
   }
}
