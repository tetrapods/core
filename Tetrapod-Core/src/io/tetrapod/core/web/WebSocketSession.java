package io.tetrapod.core.web;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.ReferenceCountUtil;
import io.tetrapod.core.Session;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.protocol.core.RequestHeader;

import java.io.IOException;

import org.slf4j.*;

public class WebSocketSession extends WebSession {

   private static final Logger logger = LoggerFactory.getLogger(WebSocketSession.class);

   public WebSocketSession(SocketChannel ch, Session.Helper helper, String contentRoot) {
      super(ch, helper);
      ch.pipeline().addLast("decoder", new HttpRequestDecoder());
      ch.pipeline().addLast("aggregator", new HttpObjectAggregator(65536));
      ch.pipeline().addLast("encoder", new HttpResponseEncoder());
      ch.pipeline().addLast("websocket", new WebSocketServerProtocolHandler(contentRoot));
      ch.pipeline().addLast("websocketHandler", this);
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
      String request = ((TextWebSocketFrame) obj).text();
      ReferenceCountUtil.release(obj);
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
