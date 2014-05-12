package io.tetrapod.core.web;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.ReferenceCountUtil;
import io.tetrapod.core.Session;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.protocol.core.RequestHeader;

import java.io.*;
import java.util.Map;

import org.slf4j.*;

public class WebSocketSession extends WebHttpSession {

   private static final Logger       logger = LoggerFactory.getLogger(WebSocketSession.class);

   private final String              wsLocation;

   private WebSocketServerHandshaker handshaker;

   public WebSocketSession(SocketChannel ch, Session.Helper helper, Map<String,WebRoot> contentRootMap, String wsLocation) {
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
      if (wsLocation.equals(req.getUri())) {
         // Handshake
         WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(wsLocation, null, false);
         handshaker = wsFactory.newHandshaker(req);
         if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
         } else {
            handshaker.handshake(ctx.channel(), req);
         }
      } else {
         super.handleHttpRequest(ctx, req);
      }
   }

   private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
      // Check for closing frame
      if (frame instanceof CloseWebSocketFrame) {
         handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
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
         RequestHeader header = webContext.makeRequestHeader(this, relayHandler.getWebRoutes());
         readRequest(header, webContext);
         return;
      } catch (IOException e) {
         logger.error("error processing websocket request", e);
         ctx.channel().writeAndFlush(new TextWebSocketFrame("Illegal request: " + request));
      }
   }

   @Override
   protected Object makeFrame(JSONObject jo) {
      return new TextWebSocketFrame(jo.toString(3));
   }

}
