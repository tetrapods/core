package io.tetrapod.core.web;

import static io.tetrapod.protocol.core.Core.*;
import io.netty.buffer.ByteBuf;
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
import io.tetrapod.protocol.core.*;

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
            final Request req = (Request) helper.make(header.contractId, header.structId);
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
      final WireSession ses = relayHandler.getRelaySession(header.toId, header.contractId);
      if (ses != null) {
         // OPTIMIZE: Find a way to relay without the byte[] allocation & copy
         String s = jo.toString();
         final ByteBuf buffer = channel.alloc().buffer(32 + s.length());
         final ByteBufDataSource data = new ByteBufDataSource(buffer);

         final Async async = new Async(null, header, this);
         int origRequestId = async.header.requestId;
         try {
            ses.addPendingRequest(async);
            logger.debug("{} RELAYING REQUEST: [{}] was " + origRequestId, this, async.header.requestId);
            buffer.writeInt(0); // length placeholder
            buffer.writeByte(ENVELOPE_JSON_REQUEST);
            async.header.write(data);
            WrappedJSON wj = new WrappedJSON(s);
            wj.write(data);
            buffer.setInt(0, buffer.writerIndex() - 4); // go back and write message length, now
                                                        // that we know it
            ses.write(buffer);
         } catch (IOException e) {
            ReferenceCountUtil.release(buffer);
            logger.error(e.getMessage(), e);
         } finally {
            header.requestId = origRequestId;
         }
      } else {
         logger.warn("Could not find a relay session for {}", header.toId);
      }
   }

}
