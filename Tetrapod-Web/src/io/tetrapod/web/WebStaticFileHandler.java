package io.tetrapod.web;

import static io.netty.handler.codec.http.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.*;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.*;

import javax.activation.MimetypesFileTypeMap;

import org.slf4j.*;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.tetrapod.core.json.*;
import io.tetrapod.core.utils.*;
import io.tetrapod.web.WebRoot.FileResult;

/**
 * A simple handler that serves incoming HTTP requests to send their respective HTTP responses. It also implements
 * {@code 'If-Modified-Since'} header to take advantage of browser cache, as described in
 * <a href="http://tools.ietf.org/html/rfc2616#section-14.25">RFC 2616</a>.
 * 
 * Adapted from netty.io example code.
 */
@Sharable
public class WebStaticFileHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

   public static final Logger          logger       = LoggerFactory.getLogger(WebStaticFileHandler.class);

   public static final int             ONE_DAY      = 24 * 60 * 60 * 1000;
   public static final int             ONE_YEAR     = 365 * ONE_DAY;

   // These rules are not correct in general, but for sites with control of their file names 
   // they are safe.  We only allow alphanumeric ascii character, ., -, _, and /.  We also do 
   // not allow .. to appear anywhere in the uri
   private static final Pattern        VALID_URI    = Pattern.compile("/[{}A-Za-z0-9._/-]*");
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

   private final Map<String, WebRoot> roots;
   private final boolean              noCaching;

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
         logger.debug("Found a non-GET Request {}", request);
         sendError(ctx, METHOD_NOT_ALLOWED);
         return;
      }
      String host = request.headers().get(HOST);
      String userAgent = request.headers().get(USER_AGENT);
      String userAgentRedirect = userAgentRedirect(userAgent, request.getUri());
      if (userAgentRedirect != null) {
         String protocol = ctx.pipeline().get("ssl") != null ? "https" : "http";
         String newLoc = String.format("%s://%s%s", protocol, host, userAgentRedirect);
         HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, MOVED_PERMANENTLY);
         response.headers().set(LOCATION, newLoc);
         response.headers().set(CONNECTION, "close");
         ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
         return;
      }
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

      String uri = request.getUri();

      if (uri.endsWith("admin")) {
         HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, MOVED_PERMANENTLY);
         response.headers().set(LOCATION, uri + "/");
         response.headers().set(CONNECTION, "close");
         ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
         return;
      }

      FileResult result = getURI(uri);
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

      addHackyHeadersForOWASP(result, request, response);

      if (result.doNotCache || noCaching) {
         // see http://stackoverflow.com/questions/49547/making-sure-a-web-page-is-not-cached-across-all-browsers
         response.headers().set(CACHE_CONTROL, NO_CACHE + ", " + NO_STORE + ", " + MUST_REVALIDATE);
         response.headers().add(PRAGMA, NO_CACHE);
         response.headers().add(EXPIRES, 0);
      } else {
         // see https://developers.google.com/speed/docs/best-practices/caching
         response.headers().set(CACHE_CONTROL, PUBLIC);
         response.headers().add(EXPIRES, new Date(System.currentTimeMillis() + ONE_YEAR));
      }

      ChannelFuture f = ctx.writeAndFlush(response);
      if (!isKeepAlive(request)) {
         f.addListener(ChannelFutureListener.CLOSE);
      }
   }

   final static LRUCache<String, Boolean> SUBDOMAIN_CACHE   = new LRUCache<>(1000);

   final static Pattern                   SUBDOMAIN_PATTERN = Pattern.compile("([^.]+)\\..*");

   private void addHackyHeadersForOWASP(FileResult result, FullHttpRequest request, HttpResponse response) {
//      if (request != null && ((result != null && result.path.endsWith(".html")) || Util.isDev())) {
//         String host = request.headers().get(HOST);
//         String referer = request.headers().get(REFERER);
//         if (referer != null && host != null) {
//            Matcher m = SUBDOMAIN_PATTERN.matcher(host);
//            if (m.matches()) {
//               String subdomain = m.group(1);
//               if (allowXFramesFromSubdomain(referer, subdomain)) {
//                  response.headers().set("X-Frame-Options", "ALLOW-FROM " + referer);
//               } else {
//                  response.headers().set("X-Frame-Options", "DENY");
//               }
//            }
//         }
//      }
      response.headers().set("X-Content-Type-Options", "nosniff");
      response.headers().set("X-XSS-Protection", "1");
      //response.headers().set("X-TetrapodDevMode", Util.getProperty("devMode"));

   }

   private boolean allowXFramesFromSubdomain(String referer, String subdomain) {
      String key = referer + ";" + subdomain;
      Boolean val = SUBDOMAIN_CACHE.get(key);
      if (val == null) {

         JSONObject jo = new JSONObject();
         jo.put("subdomain", subdomain);
         jo.put("referer", referer);

         try {
            String url = String.format("https://%s/api/v1/allowFrame", Util.getProperty("product.url"));
            if (Util.isLocal()) {
               url = String.format("http://localhost:9904/api/v1/allowFrame");
            }
            JSONObject body = Util.httpPost(url, jo.toString(), new JSONObject());
            JSONObject res = new JSONObject(body.getString("body"));
            logger.debug("{}\n\t{} => \n\t{}", url, jo.toString(3), res.toString(3));
            val = res.getString("result").equals("SUCCESS");
            SUBDOMAIN_CACHE.put(key, val);
         } catch (Exception e) {
            val = true; // fail open
            logger.error(e.getMessage());
         }
      }
      logger.debug("XFRAME {} = {}", key, val);
      return val;
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      cause.printStackTrace();
      if (ctx.channel().isActive()) {
         sendError(ctx, INTERNAL_SERVER_ERROR);
      }
   }

   private FileResult getURI(String uri) {
      FileResult res = null;
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
         final String mangledURI = uri;
         uri = unmangle(mangledURI);
         try {

            if (uri.startsWith("/home") && roots.containsKey("override")) {
               res = roots.get("override").getFile(uri);
            } else {
               for (WebRoot root : roots.values()) {
                  res = root.getFile(uri);
                  if (res != null)
                     break;
               }
            }

            if (res != null) {
               res.doNotCache = !mangledURI.startsWith("/vbf");
            }

         } catch (IOException e) {
            logger.warn("io error accessing web file", e);
         }
      }
      return res;
   }

   private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
      FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status,
            Unpooled.copiedBuffer("Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));
      response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

      if (status == NOT_FOUND) {
         response.headers().set(CACHE_CONTROL, PUBLIC);
         response.headers().add(EXPIRES, new Date(System.currentTimeMillis() + ONE_DAY));
      }

      addHackyHeadersForOWASP(null, null, response);
      ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
   }

   private void sendNotModified(ChannelHandlerContext ctx) {
      FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED);
      response.headers().set(DATE, new Date());
      ctx.writeAndFlush(response);
   }

   private String unmangle(String uri) {
      if (uri.startsWith("/vbf")) {
         uri = uri.substring(uri.indexOf("/", 2));
      }
      return uri;
   }

   private String userAgentRedirect(String userAgent, String uri) {
      logger.debug("{} - {}", uri, userAgent);
      if (userAgent == null) {
         return null;
      }
      if (!uri.equals("/") && !uri.equals("/index.html")) {
         // only redirect request for main page, saves lots of calls
         return null;
      }

      String redirect = detectStockAndroid(userAgent);
      return redirect;
   }

   private String detectStockAndroid(String userAgent) {
      // detecting stock android is frustratingly difficult.  browsers lie
      Matcher m = Pattern.compile(".*Android (\\d)[.](\\d).*").matcher(userAgent);
      if (!m.matches()) {
         return null;
      }
      int major = Integer.parseInt(m.group(1));
      int minor = Integer.parseInt(m.group(2));
      if (major > 4 || (major == 4 && minor >= 4)) {
         return null;
      }

      if (!userAgent.contains("AppleWebKit")) {
         return null;
      }

      if (userAgent.contains("Chrome")) {
         return null;
      }

      // so that should do it, but a couple more for paranoia sake
      if (userAgent.contains("Firefox")) {
         return null;
      }
      if (userAgent.contains("Opera")) {
         return null;
      }
      return null;
   }

}
