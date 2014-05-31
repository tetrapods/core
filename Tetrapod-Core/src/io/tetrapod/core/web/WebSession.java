package io.tetrapod.core.web;

import static io.tetrapod.protocol.core.Core.*;
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
   private static final Logger logger = LoggerFactory.getLogger(WebSession.class);
   
   private static final int FLOOD_TIME_PERIOD = 2000;
   private static final int FLOOD_WARN = 200;
   private static final int FLOOD_IGNORE = 300;
   private static final int FLOOD_KILL = 400;
   
   private volatile long floodPeriod;
   private final AtomicInteger requestCount = new AtomicInteger(0);

   public WebSession(SocketChannel channel, Session.Helper helper) {
      super(channel, helper);
   }

   abstract protected Object makeFrame(JSONObject jo);

   protected void readRequest(RequestHeader header, WebContext context) {
      long now = System.currentTimeMillis();
      lastHeardFrom.set(now);
      if (floodCheck(now, header)) {
         // could move flood check after comms log just for the logging
         return;
      }
      try {
         Structure request = StructureFactory.make(header.contractId, header.structId);
         if (request == null) {
            logger.error("Could not find request structure contractId={} structId-{}", header.contractId, header.structId);
            sendResponse(new Error(ERROR_SERIALIZATION), header.requestId);
            return;
         }
         request.read(new WebJSONDataSource(context.getRequestParams(), request.tagWebNames()));
         commsLog("%s  [%d] <- %s", this, header.requestId, request.dump());
         
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
   
   private void relayRequest(RequestHeader header, Structure request) throws IOException {
      final Session ses = relayHandler.getRelaySession(header.toId, header.contractId);
      if (ses != null) {
         final ByteBuf in = convertToByteBuf(request);
         try {
            ses.sendRelayedRequest(header, in, this);
         } finally {
            in.release();
         }
      } else {
         logger.debug("Could not find a relay session for {} {}", header.toId, header.contractId);
         sendResponse(new Error(ERROR_SERVICE_UNAVAILABLE), header.requestId);
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
         JSONDataSource jd = new WebJSONDataSource(jo, payload.tagWebNames());
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
         Structure payload = StructureFactory.make(jo.optInt("_contractId"), jo.optInt("_structId"));
         ByteBufDataSource bd = new ByteBufDataSource(payloadBuf);
         payload.read(bd);
         JSONDataSource jd = new WebJSONDataSource(jo, payload.tagWebNames());
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
            jo.put("_topicId", messH.topicId);
            break;
      }
      return jo;
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

}
