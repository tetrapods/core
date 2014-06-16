package io.tetrapod.core.web;

import static io.tetrapod.protocol.core.Core.DIRECT;
import static io.tetrapod.protocol.core.CoreContract.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.ReferenceCountUtil;
import io.tetrapod.core.*;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.protocol.core.RequestHeader;

import java.io.IOException;
import java.util.Map;

import org.slf4j.*;

import com.codahale.metrics.*;
import com.codahale.metrics.Timer.Context;

public class WebSocketSession extends WebHttpSession {

   private static final Logger       logger            = LoggerFactory.getLogger(WebSocketSession.class);

   private static final int          FLOOD_TIME_PERIOD = 2000;
   private static final int          FLOOD_WARN        = 200;
   private static final int          FLOOD_IGNORE      = 300;
   private static final int          FLOOD_KILL        = 400;

   private volatile long             floodPeriod;

   public static final Timer         requestTimes      = Metrics.timer(WebSocketSession.class, "requests", "time");

   private final String              wsLocation;

   private int                       reqCounter        = 0;

   private WebSocketServerHandshaker handshaker;

   public WebSocketSession(SocketChannel ch, Session.Helper helper, Map<String, WebRoot> contentRootMap, String wsLocation) {
      super(ch, helper, contentRootMap);
      this.wsLocation = wsLocation;
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof FullHttpRequest) {
         handleHttpRequest(ctx, (FullHttpRequest) msg);
      } else if (msg instanceof WebSocketFrame) {
         handleWebSocketFrame(ctx, (WebSocketFrame) msg);
      }
   }

   @Override
   public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
      ctx.flush();
   }

   @Override
   protected void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
      if (logger.isDebugEnabled()) {
         synchronized (this) {
            logger.debug(String.format("### %s REQUEST[%d] = %s : %s", this, ++reqCounter, ctx.channel().remoteAddress(), req.getUri()));
         }
      }
      final Context context = requestTimes.time();
      if (wsLocation.equals(req.getUri())) {
         // Handshake
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
         super.handleHttpRequest(ctx, req);
      }
      context.stop();
   }

   private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
      // Check for closing frame
      if (frame instanceof CloseWebSocketFrame) {
         handshaker.close(ctx.channel(), new CloseWebSocketFrame());
         return;
      }
      if (frame instanceof PingWebSocketFrame) {
         ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
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
         readAndDispatchRequest(header, webContext);
         return;
      } catch (IOException e) {
         logger.error("error processing websocket request", e);
         ctx.channel().writeAndFlush(new TextWebSocketFrame("Illegal request: " + request));
      }
   }

   @Override
   protected Object makeFrame(JSONObject jo, boolean keepAlive) {
      synchronized (this) {
         if (handshaker == null) {
            return super.makeFrame(jo, keepAlive);
         }
      }
      return new TextWebSocketFrame(jo.toString(3));
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

   protected void readAndDispatchRequest(RequestHeader header, WebContext context) {
      long now = System.currentTimeMillis();
      lastHeardFrom.set(now);

      if (floodCheck(now, header)) {
         // could move flood check after comms log just for the logging
         return;
      }
      try {
         Structure request = readRequest(header, context);

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

}
