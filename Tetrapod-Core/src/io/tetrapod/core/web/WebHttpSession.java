package io.tetrapod.core.web;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import io.tetrapod.core.Session;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.protocol.core.RequestHeader;

import java.io.File;

public class WebHttpSession extends WebSession {
   
   private static File[] splitContentRoot(String contentRoot) {
      String[] parts = contentRoot.split(":");
      File[] res = new File[parts.length];
      for (int i = 0; i < res.length; i++) {
         res[i] = new File(parts[i]);
      }
      return res;
   }

   private boolean isKeepAlive;

   public WebHttpSession(SocketChannel ch, Session.Helper helper, String contentRoot) {
      super(ch, helper);
      
      // Uncomment the following lines if you want HTTPS
      // SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
      // engine.setUseClientMode(false);
      // ch.pipeline().addLast("ssl", new SslHandler(engine));
      
      ch.pipeline().addLast("decoder", new HttpRequestDecoder());
      ch.pipeline().addLast("aggregator", new HttpObjectAggregator(65536));
      ch.pipeline().addLast("encoder", new HttpResponseEncoder());
      ch.pipeline().addLast("api", this);
      if (contentRoot != null) {
         WebStaticFileHandler sfh = new WebStaticFileHandler(false, splitContentRoot(contentRoot));
         ch.pipeline().addLast("files", sfh);
      }
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
      if (!(obj instanceof HttpRequest)) {
         ctx.fireChannelRead(obj);
         return;
      }
      WebContext context = new WebContext((HttpRequest)obj);
      RequestHeader header = context.makeRequestHeader(this, relayHandler.getWebRoutes());
      if (header == null) {
         ctx.fireChannelRead(obj);
         return;
      }
      isKeepAlive =  HttpHeaders.isKeepAlive((HttpRequest)obj);
      ReferenceCountUtil.release(obj);
      readRequest(header, context);
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

}
