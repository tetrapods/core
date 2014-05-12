package io.tetrapod.core.web;

import static io.netty.handler.codec.http.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.*;
import io.tetrapod.core.Session;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.protocol.core.RequestHeader;

import java.util.Map;

import org.slf4j.*;

public class WebHttpSession extends WebSession {
   protected static final Logger logger = LoggerFactory.getLogger(WebHttpSession.class);

   private boolean               isKeepAlive;

   public WebHttpSession(SocketChannel ch, Session.Helper helper, Map<String, WebRoot> roots) {
      super(ch, helper);

      final boolean usingSSL = ch.pipeline().get(SslHandler.class) != null;

      ch.pipeline().addLast("codec-http", new HttpServerCodec());
      ch.pipeline().addLast("aggregator", new HttpObjectAggregator(65536));
      ch.pipeline().addLast("api", this);
      ch.pipeline().addLast("chunkedWriter", new ChunkedWriteHandler());
      WebStaticFileHandler sfh = new WebStaticFileHandler(usingSSL, roots);
      ch.pipeline().addLast("files", sfh);
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (!(msg instanceof HttpRequest)) {
         ctx.fireChannelRead(msg);
         return;
      }
      handleHttpRequest(ctx, (FullHttpRequest) msg);
   }

   protected void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
      if (!req.getDecoderResult().isSuccess()) {
         sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
         return;
      }

      // handle a JSON API call
      WebContext context = new WebContext(req);
      RequestHeader header = context.makeRequestHeader(this, relayHandler.getWebRoutes());
      if (header == null) {
         ctx.fireChannelRead(req);
         return;
      }
      isKeepAlive = HttpHeaders.isKeepAlive((HttpRequest) req);
      ReferenceCountUtil.release(req);
      readRequest(header, context);
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
   public void checkHealth() {
      // TODO?      
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

}
