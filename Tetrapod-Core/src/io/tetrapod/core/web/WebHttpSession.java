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
import io.tetrapod.core.Session;
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
            isKeepAlive = HttpHeaders.isKeepAlive((HttpRequest) req);
            ReferenceCountUtil.release(req);
            Structure request = readRequest(header, context);
            if (request != null) {
               header.fromType = Core.TYPE_WEBAPI;
               relayRequest(header, request).handle(new ResponseHandler() {
                  @Override
                  public void onResponse(Response res) {
                     try {
                        // Mystery: calling this goes into a rabbit hole: sendResponse(res, header.requestId);
                        // TODO: Make this JSON Response tastier
                        ctx.writeAndFlush(makeFrame(res, header.requestId)).addListener(ChannelFutureListener.CLOSE);                        
                     } catch (Throwable e) {
                        logger.error(e.getMessage(), e);
                     }
                  }
               });
            }
         } else {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
         }
      } else {
         ctx.fireChannelRead(req);
      }
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

   protected Async relayRequest(RequestHeader header, Structure request) throws IOException {
      final Session ses = relayHandler.getRelaySession(header.toId, header.contractId);
      if (ses != null) {
         final ByteBuf in = convertToByteBuf(request);
         try {
            return ses.sendRelayedRequest(header, in, this);
         } finally {
            in.release();
         }
      } else {
         logger.debug("Could not find a relay session for {} {}", header.toId, header.contractId);
         sendResponse(new Error(ERROR_SERVICE_UNAVAILABLE), header.requestId);
      }
      return null;
   }

   protected ByteBuf convertToByteBuf(Structure struct) throws IOException {
      ByteBuf buffer = channel.alloc().buffer(32);
      ByteBufDataSource data = new ByteBufDataSource(buffer);
      struct.write(data);
      return buffer;
   }
}
