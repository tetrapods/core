package io.tetrapod.web;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.tetrapod.core.utils.AdminAuthToken;
import io.tetrapod.core.utils.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.Date;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Names.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaders.Names.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class MaintenanceHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
   public static final Logger logger = LoggerFactory.getLogger(MaintenanceHandler.class);
   private final WebRoot webRoot;

   public MaintenanceHandler(WebRoot tetrapod) {
      webRoot = tetrapod;
   }

   @Override
   protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {

      String uri = request.getUri();
      if (!uri.startsWith("/admin") && !uri.startsWith("/protocol")) {
         String maintenanceMode = Util.getProperty("maintenanceMode","");
         if (!Util.isEmpty(maintenanceMode)) {
            String cookieHeader = request.headers().get("Cookie");
            String authToken = "";
            if (!Util.isEmpty(cookieHeader)) {
               Set<Cookie> cookies = ServerCookieDecoder.LAX.decode(cookieHeader);
               for (Cookie cookie : cookies) {
                  if (cookie.name().equals("auth-token")) {
                     authToken = cookie.value();
                     break;
                  }
               }

               if (!Util.isEmpty(authToken)) {
                  authToken = URLDecoder.decode(authToken, "UTF-8");
               }
            }
            if (AdminAuthToken.decodeLoginToken(authToken) == 0) {
               HttpResponse response = getResponse(maintenanceMode);
               ChannelFuture f = ctx.writeAndFlush(response);
               f.addListener(ChannelFutureListener.CLOSE);
               return;
            }
         }
      }

      ReferenceCountUtil.retain(request);
      ctx.fireChannelRead(request);
   }

   private HttpResponse getResponse(String maintenanceMode) {
      WebRoot.FileResult result = null;
      HttpResponse response;

      try {
         result = webRoot.getFile("/maintenance.html");
      } catch (IOException e) {
         logger.error("Can't find maintenance.html", e);
      }

      if (result != null) {
         byte[] contents = new String(result.contents).replace("[[MAINTENANCE_TIME]]", maintenanceMode).getBytes();
         long fileLength = contents.length;

         response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(contents));
         setContentLength(response, fileLength);
         response.headers().set("Chatbox-Maintenance", maintenanceMode);
         response.headers().set(CONTENT_TYPE, "text/html");
         response.headers().set(DATE, new Date());
         response.headers().set(LAST_MODIFIED, new Date(result.modificationTime));
         // see http://stackoverflow.com/questions/49547/making-sure-a-web-page-is-not-cached-across-all-browsers
         response.headers().set(CACHE_CONTROL, NO_CACHE + ", " + NO_STORE + ", " + MUST_REVALIDATE);
         response.headers().add(PRAGMA, NO_CACHE);
         response.headers().add(EXPIRES, 0);
      } else {
         response = new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR, Unpooled.copiedBuffer("Failure\r\n", CharsetUtil.UTF_8));
         response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
      }
      return response;
   }
}
