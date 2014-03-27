package io.tetrapod.core.web;

import static io.tetrapod.protocol.core.Core.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.*;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.datasources.*;
import io.tetrapod.protocol.core.*;

import java.io.IOException;

import org.slf4j.*;

abstract class WebSession extends Session {
   private static final Logger logger = LoggerFactory.getLogger(WebSession.class);

   public WebSession(SocketChannel channel, Session.Helper helper) {
      super(channel, helper);
   }
   
   abstract protected Object makeFrame(JSONObject jo);
   
   protected void readRequest(RequestHeader header, WebContext context) {
      try {
         Request request = (Request)StructureFactory.make(header.contractId, header.structId);
         if (request == null) {
            logger.error("Could not find request structure contractId={} structId{}", header.contractId, header.structId);
            sendResponse(new Error(ERROR_SERIALIZATION), header.requestId);
            return;
         }
         request.read(new WebJSONDataSource(context.getRequestParams(), request.tagWebNames()));
         if ((header.toId == UNADDRESSED && header.contractId == myContractId) || header.toId == myId) {
            dispatchRequest(header, request);
         } else {
            relayRequest(header, request);
         }

      } catch (IOException e) {
         logger.error("Error processing request {}", header.dump());
         sendResponse(new Error(ERROR_UNKNOWN), header.requestId);
      }
   }
   
   private void relayRequest(RequestHeader header, Request request) throws IOException {
      final Session ses = relayHandler.getRelaySession(header.toId, header.contractId);
      if (ses != null) {
         ByteBuf in = convertToByteBuf(request);
         ses.sendRelayedRequest(header, in, this);
      } else {
         logger.warn("Could not find a relay session for {}", header.toId);
      }
   }

   private ByteBuf convertToByteBuf(Structure struct) throws IOException {
      ByteBuf buffer = channel.alloc().buffer(32);
      ByteBufDataSource data = new ByteBufDataSource(buffer);
      struct.write(data);
      return buffer;
   }

   @Override
   protected Object makeFrame(Structure header, Structure payload, byte envelope) {
      try {
         JSONObject jo = extractHeader(header);
         JSONDataSource jd = new JSONDataSource(jo);
         payload.write(jd);
         return makeFrame(jo);
      } catch (IOException e) {
         logger.error("Could not make web frame for {}", header.dump());
         return null;
      }
   }

   @Override
   protected Object makeFrame(Structure header, ByteBuf payloadBuf, byte envelope) {
      try {
         JSONObject jo = extractHeader(header);
         Structure payload = StructureFactory.make(jo.optInt("contractId"), jo.optInt("structId"));
         ByteBufDataSource bd = new ByteBufDataSource(payloadBuf);
         payload.read(bd);
         JSONDataSource jd = new JSONDataSource(jo);
         payload.write(jd);
         return makeFrame(jo);
      } catch (IOException e) {
         logger.error("Could not make web frame for {}", header.dump());
         return null;
      }
   }
   
   private JSONObject extractHeader(Structure header) {
      JSONObject jo = new JSONObject();
      switch (header.getStructId()) {
         case RequestHeader.STRUCT_ID:
            RequestHeader reqH = (RequestHeader)header;
            jo.put("contractId", reqH.contractId);
            jo.put("structId", reqH.structId);
            jo.put("requestId", reqH.requestId);
            break;
            
         case ResponseHeader.STRUCT_ID:
            ResponseHeader respH = (ResponseHeader)header;
            jo.put("contractId", respH.contractId);
            jo.put("structId", respH.structId);
            jo.put("requestId", respH.requestId);
            break;
            
         case MessageHeader.STRUCT_ID:
            MessageHeader messH = (MessageHeader)header;
            jo.put("contractId", messH.contractId);
            jo.put("structId", messH.structId);
            jo.put("topicId", messH.topicId);
            break;
      }
      return jo;
   }

}
