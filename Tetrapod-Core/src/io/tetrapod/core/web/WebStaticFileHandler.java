package io.tetrapod.core.web;

import static io.netty.handler.codec.http.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.tetrapod.core.utils.Util;
import io.tetrapod.core.web.WebRoot.FileResult;

import java.io.*;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.Pattern;

import javax.activation.MimetypesFileTypeMap;

import org.slf4j.*;

/**
 * A simple handler that serves incoming HTTP requests to send their respective HTTP responses. It also implements
 * {@code 'If-Modified-Since'} header to take advantage of browser cache, as described in <a
 * href="http://tools.ietf.org/html/rfc2616#section-14.25">RFC 2616</a>.
 * 
 * Adapted from netty.io example code.
 */
@Sharable
class WebStaticFileHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

   public static final Logger          logger       = LoggerFactory.getLogger(WebStaticFileHandler.class);

   public static final int             ONE_YEAR     = 365 * 24 * 60 * 60 * 1000;

   // These rules are not correct in general, but for sites with control of their file names 
   // they are safe.  We only allow alphanumeric ascii character, ., -, _, and /.  We also do 
   // not allow .. to appear anywhere in the uri
   private static final Pattern        VALID_URI    = Pattern.compile("/[A-Za-z0-9._/-]*");
   private static final Pattern        INVALID_URI  = Pattern.compile(".*[.][.].*");

   private static MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
   static {
      mimeTypesMap.addMimeTypes("text/plain txt text TXT TEXT");
      mimeTypesMap.addMimeTypes("text/html html HTML htm HTM");
      mimeTypesMap.addMimeTypes("text/javascript js");
      mimeTypesMap.addMimeTypes("text/json json");
      mimeTypesMap.addMimeTypes("text/css css CSS");
      mimeTypesMap.addMimeTypes("image/png png PNG");
      mimeTypesMap.addMimeTypes("image/jpeg jpg JPG");
      mimeTypesMap.addMimeTypes("image/gif gif GIF");
      mimeTypesMap.addMimeTypes("application/x-shockwave-flash swf SWF");
   }

   private final Map<String, WebRoot>  roots;
   private final boolean noCaching;

   public WebStaticFileHandler(Map<String, WebRoot> roots) {
      this.roots = roots;
      this.noCaching = Util.getProperty("web.nocache", false);
   }

   @Override
   public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
      if (!request.getDecoderResult().isSuccess()) {
         sendError(ctx, BAD_REQUEST);
         return;
      }
      if (request.getMethod() != GET) {
         sendError(ctx, METHOD_NOT_ALLOWED);
         return;
      }
      String host = request.headers().get(HOST);
      if (host != null && host.toLowerCase().startsWith("www.")) {
         host = host.substring(4);
         String protocol = ctx.pipeline().get("ssl") != null ? "https" : "http";
         String newLoc = String.format("%s://%s%s", protocol, host, request.getUri());
         HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, MOVED_PERMANENTLY);
         response.headers().set(LOCATION, newLoc);
         response.headers().set(CONNECTION, "close");
         ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
         return;
      }
      
      FileResult result = getURI(request.getUri());

      if (result == null) {
         sendError(ctx, NOT_FOUND);
         return;
      }

      // Cache Validation
      Date ifModifiedSinceDate = HttpHeaders.getDateHeader(request, IF_MODIFIED_SINCE, null);
      if (ifModifiedSinceDate != null) {
         // Only compare up to the second because the datetime format we send to
         // the client does not have milliseconds
         long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
         long fileLastModifiedSeconds = result.modificationTime / 1000;
         if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
            sendNotModified(ctx);
            return;
         }
      }

      long fileLength = result.contents.length;

      HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(result.contents));
      setContentLength(response, fileLength);
      response.headers().set(CONTENT_TYPE, mimeTypesMap.getContentType(result.path));
      response.headers().set(DATE, new Date());
      response.headers().set(LAST_MODIFIED, new Date(result.modificationTime));
      if (isKeepAlive(request)) {
         response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
      }
      if (result.isIndex || noCaching) {
         // see http://stackoverflow.com/questions/49547/making-sure-a-web-page-is-not-cached-across-all-browsers
         response.headers().set(CACHE_CONTROL, new String[] { NO_CACHE, NO_STORE, MUST_REVALIDATE });
         response.headers().add(PRAGMA, NO_CACHE);
         response.headers().add(EXPIRES, 0);
      } else {
         // see https://developers.google.com/speed/docs/best-practices/caching
         response.headers().set(CACHE_CONTROL, PUBLIC);
         response.headers().add(EXPIRES, new Date(System.currentTimeMillis() + ONE_YEAR));
      }

      ChannelFuture f = ctx.writeAndFlush(response);
      //      f = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
      if (!isKeepAlive(request)) {
         f.addListener(ChannelFutureListener.CLOSE);
      }
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      cause.printStackTrace();
      if (ctx.channel().isActive()) {
         sendError(ctx, INTERNAL_SERVER_ERROR);
      }
   }

   private FileResult getURI(String uri) {
      int qIx = uri.indexOf('?');
      if (qIx > 0) {
         uri = uri.substring(0, qIx);
      }
      try {
         uri = URLDecoder.decode(uri, "UTF-8");
      } catch (UnsupportedEncodingException e) {
         try {
            uri = URLDecoder.decode(uri, "ISO-8859-1");
         } catch (UnsupportedEncodingException e1) {
            throw new Error();
         }
      }
      if (VALID_URI.matcher(uri).matches() && !INVALID_URI.matcher(uri).matches()) {
         uri = mangle(uri);
         for (WebRoot root : roots.values()) {
            try {
               FileResult r = root.getFile(uri);
               if (r != null)
                  return r;
            } catch (IOException e) {
               logger.warn("io error accessing web file", e);
            }
         }
      }
      return null;
   }

   private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
      FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status.toString()
            + "\r\n", CharsetUtil.UTF_8));
      response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
      ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
   }

   private void sendNotModified(ChannelHandlerContext ctx) {
      FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED);
      response.headers().set(DATE, new Date());
      ctx.writeAndFlush(response);
   }

   private String mangle(String uri) {
      if (uri.startsWith("/vbf")) {
         uri = uri.substring(uri.indexOf("/", 2));
      }
      return uri;
   }

}
