package io.tetrapod.core.web;

import static io.netty.handler.codec.http.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;

import java.io.*;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import javax.activation.*;

/**
 * A simple handler that serves incoming HTTP requests to send their respective
 * HTTP responses. It also implements {@code 'If-Modified-Since'} header to take
 * advantage of browser cache, as described in <a
 * href="http://tools.ietf.org/html/rfc2616#section-14.25">RFC 2616</a>.
 * 
 * Adapted from netty.io example code.
 */
@Sharable
class WebStaticFileHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

   public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
   public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
   public static final int HTTP_CACHE_SECONDS = 60;
   
   // These rules are not correct in general, but for sites with control of their file names 
   // they are safe.  We only allow alphanumeric ascii character, ., -, _, and /.  We also do 
   // not allow .. to appear anywhere in the uri
   private static final Pattern VALID_URI = Pattern.compile("/[A-Za-z0-9._/-]+");
   private static final Pattern INVALID_URI = Pattern.compile(".*[.][.].*");
   
   private static MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
   static {
      mimeTypesMap.addMimeTypes("text/plain txt text TXT TEXT");
      mimeTypesMap.addMimeTypes("text/html html HTML htm HTM");
      mimeTypesMap.addMimeTypes("text/css css CSS");
      mimeTypesMap.addMimeTypes("image/png png PNG");
      mimeTypesMap.addMimeTypes("image/jpeg jpg JPG");
      mimeTypesMap.addMimeTypes("image/gif gif GIF");
   }


   private final boolean useSendFile;
   private final Map<String,File> rootDirs;

   public WebStaticFileHandler(boolean usingSSL, Map<String,File> rootDirs) {
      this.useSendFile = !usingSSL;
      this.rootDirs = rootDirs;
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
      final File file = decodePath(request.getUri());
      if (file == null) {
         sendError(ctx, FORBIDDEN);
         return;
      }
      if (file.isHidden() || !file.exists()) {
         sendError(ctx, NOT_FOUND);
         return;
      }
      if (!file.isFile()) {
         sendError(ctx, FORBIDDEN);
         return;
      }

      // Cache Validation
      String ifModifiedSince = request.headers().get(IF_MODIFIED_SINCE);
      if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
         SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
         Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

         // Only compare up to the second because the datetime format we send to
         // the client does not have milliseconds
         long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
         long fileLastModifiedSeconds = file.lastModified() / 1000;
         if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
            sendNotModified(ctx);
            return;
         }
      }

      RandomAccessFile raf;
      try {
         raf = new RandomAccessFile(file, "r");
      } catch (FileNotFoundException fnfe) {
         sendError(ctx, NOT_FOUND);
         return;
      }
      long fileLength = raf.length();

      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
      setContentLength(response, fileLength);
      setContentTypeHeader(response, file);
      setDateAndCacheHeaders(response, file);
      if (isKeepAlive(request)) {
         response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
      }

      // Write the initial line and the header.
      ctx.write(response);

      // Write the content.
      if (useSendFile) {
         ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength), ctx.newProgressivePromise());
      } else {
         ctx.write(new ChunkedFile(raf, 0, fileLength, 8192), ctx.newProgressivePromise());
      }

      // Write the end marker
      ChannelFuture f = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

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

   private File decodePath(String uri) {
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
         for (File rootDir : rootDirs.values()) {
            File f = new File(rootDir, uri);
            if (f.exists()) {
               if (f.isDirectory()) {
                  f = new File(f, "index.html");
                  if (f.exists())
                     return f;
                  else
                     continue;
               }
               return f;
            }
         }
      }
      return null;
   }

   private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
      FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));
      response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
      ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
   }

   private void sendNotModified(ChannelHandlerContext ctx) {
      FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED);
      setDateHeader(response);
      ctx.writeAndFlush(response);
   }

   private void setDateHeader(FullHttpResponse response) {
      SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
      dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
      Calendar time = new GregorianCalendar();
      response.headers().set(DATE, dateFormatter.format(time.getTime()));
   }

   private void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
      SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
      dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

      // Date header
      Calendar time = new GregorianCalendar();
      response.headers().set(DATE, dateFormatter.format(time.getTime()));

      // Add cache headers
      time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
//      response.headers().set(EXPIRES, dateFormatter.format(time.getTime()));
//      response.headers().set(CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
      response.headers().set(LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
   }

   private void setContentTypeHeader(HttpResponse response, File file) {
      response.headers().set(CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
   }
   



}