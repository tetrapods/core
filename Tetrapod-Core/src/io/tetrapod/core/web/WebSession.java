package io.tetrapod.core.web;

import static io.tetrapod.protocol.core.CoreContract.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.*;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.datasources.*;
import io.tetrapod.protocol.core.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.*;

abstract class WebSession extends Session {
   private static final Logger   logger       = LoggerFactory.getLogger(WebSession.class);

   protected final AtomicInteger requestCount = new AtomicInteger(0);

   public WebSession(SocketChannel channel, Session.Helper helper) {
      super(channel, helper);
   }

   abstract protected Object makeFrame(JSONObject jo, boolean keepAlive);

   protected Structure readRequest(RequestHeader header, WebContext context) throws IOException {
      Structure request = StructureFactory.make(header.contractId, header.structId);
      if (request == null) {
         logger.error("Could not find request structure contractId={} structId-{}", header.contractId, header.structId);
         sendResponse(new Error(ERROR_SERIALIZATION), header.requestId);
         return null;
      }
      request.read(new WebJSONDataSource(context.getRequestParams(), request.tagWebNames()));
      commsLog("%s  [%d] <- %s", this, header.requestId, request.dump());
      return request;
   }

   @Override
   protected Object makeFrame(Structure header, Structure payload, byte envelope) {
      try {
         JSONObject jo = extractHeader(header);
         JSONDataSource jd = new WebJSONDataSource(jo, payload.tagWebNames());
         payload.write(jd);
         return makeFrame(jo, true);
      } catch (IOException e) {
         logger.error("Could not make web frame for {}", header.dump());
         return null;
      }
   }

   @Override
   protected Object makeFrame(Structure header, ByteBuf payloadBuf, byte envelope) {
      try {
         JSONObject jo = extractHeader(header);
         Structure payload = StructureFactory.make(jo.optInt("_contractId"), jo.optInt("_structId"));
         ByteBufDataSource bd = new ByteBufDataSource(payloadBuf);
         payload.read(bd);
         JSONDataSource jd = new WebJSONDataSource(jo, payload.tagWebNames());
         payload.write(jd);
         return makeFrame(jo, true);
      } catch (IOException e) {
         logger.error("Could not make web frame for {}", header.dump());
         return null;
      }
   }

   protected JSONObject extractHeader(Structure header) {
      JSONObject jo = new JSONObject();
      switch (header.getStructId()) {
         case RequestHeader.STRUCT_ID:
            RequestHeader reqH = (RequestHeader) header;
            jo.put("_contractId", reqH.contractId);
            jo.put("_structId", reqH.structId);
            jo.put("_requestId", reqH.requestId);
            break;

         case ResponseHeader.STRUCT_ID:
            ResponseHeader respH = (ResponseHeader) header;
            jo.put("_contractId", respH.contractId);
            jo.put("_structId", respH.structId);
            jo.put("_requestId", respH.requestId);
            break;

         case MessageHeader.STRUCT_ID:
            MessageHeader messH = (MessageHeader) header;
            jo.put("_contractId", messH.contractId);
            jo.put("_structId", messH.structId);
            jo.put("_topicId", messH.toType == MessageHeader.TO_TOPIC ? messH.toId : 0);
            break;
      }
      return jo;
   }

}
