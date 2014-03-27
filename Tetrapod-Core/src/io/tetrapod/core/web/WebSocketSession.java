package io.tetrapod.core.web;

import static io.tetrapod.protocol.core.Core.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.ReferenceCountUtil;
import io.tetrapod.core.*;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.datasources.*;
import io.tetrapod.protocol.core.RequestHeader;

import java.io.IOException;

import org.slf4j.*;

class WebSocketSession extends Session {

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

         if ((header.toId == UNADDRESSED && header.contractId == myContractId) || header.toId == myId) {
            final Request req = (Request) StructureFactory.make(header.contractId, header.structId);
            if (req != null) {
               req.read(new WebJSONDataSource(jo, req.tagWebNames()));
               dispatchRequest(header, req);
            } else {
               logger.warn("Could not find request structure {}", header.structId);
               sendResponse(new Error(ERROR_SERIALIZATION), header.requestId);
            }
         } else if (relayHandler != null) {
            relayRequest(header, jo);
         }

      } catch (IOException e) {
      }
      ctx.channel().writeAndFlush(new TextWebSocketFrame("Illegal request: " + request));
   }
   
   @Override
   public Async sendRequest(Request req, int toId, byte timeoutSeconds) {
      logger.error("can't send requests to clients {}", req.dump());
      return null;
   }

   @Override
   protected void sendResponse(Response res, int requestId) {
      JSONDataSource jds = new WebJSONDataSource(res.tagWebNames());
      try {
         res.write(jds);
      } catch (IOException e) {
         logger.error(e.getMessage(), e);
         return;
      }
      jds.getJSON().put("response", requestId);
      jds.getJSON().put("structId", res.getStructId());
      jds.getJSON().put("contractId", res.getContractId());
      channel.writeAndFlush(new TextWebSocketFrame(jds.getJSON().toString()));
      // someday perhaps we go binary and speak wire protocol?  requires code gen where json results
      // can more or less be consumed by hand written code
      // new BinaryWebSocketFrame(makeByteBufResult(result));
   }

   @Override
   public void sendMessage(Message msg, int toEntityId, int topicId) {
      // TODO
   }

   private void relayRequest(final RequestHeader header, final JSONObject jo) {
      // TODO implement better
   }

}
