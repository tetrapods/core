package io.tetrapod.core.logging;

import java.io.*;
import java.lang.reflect.Field;
import java.time.*;
import java.util.TimeZone;

import org.slf4j.*;

import io.tetrapod.core.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.datasources.*;
import io.tetrapod.protocol.core.*;

public class CommsLogEntry {

   public static final Logger  logger = LoggerFactory.getLogger(CommsLogEntry.class);

   public final CommsLogHeader header;
   public final Structure      struct;
   public final Object         payload;

   public CommsLogEntry(CommsLogHeader header, Structure struct, Object payload) {
      assert header != null;
      assert struct != null;
      assert payload != null;

      this.header = header;
      this.struct = struct;
      this.payload = payload;
   }

   private static Structure readPayload(IOStreamDataSource data, CommsLogHeader header, int contractId, int structId) throws IOException {
      Structure payload = StructureFactory.make(contractId, structId);
      assert payload != null;
      if (payload != null) {
         payload.read(data);
      }
      return payload;
   }

   public static CommsLogEntry read(IOStreamDataSource data) throws IOException {
      CommsLogHeader header = new CommsLogHeader();
      header.read(data);
      if (header.type == null)
         throw new IOException();
      switch (header.type) {
         case MESSAGE: {
            MessageHeader msgHeader = new MessageHeader();
            msgHeader.read(data);
            return new CommsLogEntry(header, msgHeader, readPayload(data, header, msgHeader.contractId, msgHeader.structId));
         }
         case REQUEST: {
            RequestHeader reqHeader = new RequestHeader();
            reqHeader.read(data);
            return new CommsLogEntry(header, reqHeader, readPayload(data, header, reqHeader.contractId, reqHeader.structId));
         }
         case RESPONSE: {
            ResponseHeader resHeader = new ResponseHeader();
            resHeader.read(data);
            return new CommsLogEntry(header, resHeader, readPayload(data, header, resHeader.contractId, resHeader.structId));
         }
         case EVENT:
            throw new RuntimeException("Not yet supported");
      }
      return null;
   }

   public void write(DataOutputStream out) throws IOException {
      assert header != null;
      assert struct != null;
      assert payload != null;

      IOStreamDataSource data = IOStreamDataSource.forWriting(out);
      header.write(data);
      struct.write(data);

      Structure struct = getPayloadStruct();
      sanitize(struct);
      struct.write(data);
   }

   private Structure getPayloadStruct() {
      Structure struct = null;
      if (payload instanceof Structure) {
         struct = (Structure) payload;
      } else {
         struct = makeStructFromHeader();
         if (struct != null) {
            try {
               struct.read(TempBufferDataSource.forReading((byte[]) payload));
            } catch (IOException e) {}
         }
      }
      if (struct == null) {
         struct = new MissingStructDef();
      }
      return struct;
   }

   private Structure makeStructFromHeader() {
      switch (header.type) {
         case MESSAGE:
            return StructureFactory.make(((MessageHeader) struct).contractId, ((MessageHeader) struct).structId);
         case REQUEST:
            return StructureFactory.make(((RequestHeader) struct).contractId, ((RequestHeader) struct).structId);
         case RESPONSE:
            return StructureFactory.make(((ResponseHeader) struct).contractId, ((ResponseHeader) struct).structId);
         default:
            return null;
      }
   }

   private void sanitize(Structure struct) throws IOException {
      try { 
         for (Field f : struct.getClass().getFields()) {
            if (struct.isSensitive(f.getName())) {
               if (f.getType().isPrimitive()) {
                  if (f.getType() == int.class) {
                     f.setInt(struct, 0);
                  } else if (f.getType() == long.class) {
                     f.setLong(struct, 0L);
                  } else if (f.getType() == byte.class) {
                     f.setByte(struct, (byte) 0);
                  } else if (f.getType() == short.class) {
                     f.setShort(struct, (short) 0);
                  } else if (f.getType() == boolean.class) {
                     f.setBoolean(struct, false);
                  } else if (f.getType() == boolean.class) {
                     f.setChar(struct, (char) 0);
                  } else if (f.getType() == double.class) {
                     f.setDouble(struct, 0.0);
                  } else if (f.getType() == float.class) {
                     f.setFloat(struct, 0.0f);
                  }

               } else {
                  f.set(struct, null);
               }
            }
         }
      } catch (Exception e) {
         throw new IOException(e);
      }
   }

   public boolean matches(long minTime, long maxTime, long contextId) {
      if (header.timestamp >= minTime && header.timestamp <= maxTime) {
         switch (header.type) {
            case MESSAGE: {
               //MessageHeader h = (MessageHeader) struct;
               return false;
            }
            case REQUEST: {
               RequestHeader h = (RequestHeader) struct;
               return h.contextId == contextId;
            }
            case RESPONSE: {
               ResponseHeader h = (ResponseHeader) struct;
               return h.contextId == contextId;
            }
            case EVENT:
               return false;
         }
      }
      return false;
   }

   public static String getNameFor(ResponseHeader header) {
      return StructureFactory.getName(header.contractId, header.structId);
   }

   public static String getNameFor(MessageHeader header) {
      return StructureFactory.getName(header.contractId, header.structId);
   }

   public static String getNameFor(RequestHeader header) {
      return StructureFactory.getName(header.contractId, header.structId);
   }

   @Override
   public String toString() {
      String ses = (header.sesType == SessionType.WEB ? "Http" : "Wire");

      String direction = header.sending ? "->" : "<-";
      long contextId = 0;
      String details = null;
      LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochSecond(header.timestamp / 1000), TimeZone.getDefault().toZoneId());
      String time = dt.toString();
      String name = null;

      Structure s = getPayloadStruct();
      String payloadDetails = "";
      if (s != null && s.getStructId() != Success.STRUCT_ID && s.getStructId() != io.tetrapod.core.rpc.Error.STRUCT_ID) {
         payloadDetails = s.dump();
      }
      switch (header.type) {
         case MESSAGE: {
            MessageHeader h = (MessageHeader) struct;
            //boolean isBroadcast = h.toChildId == 0 && h.topicId != 1;
            name = getNameFor(h);
            details = String.format("to %d.%d t%d f%d", h.toParentId, h.toChildId, h.topicId, h.flags);
            break;
         }
         case REQUEST: {
            RequestHeader h = (RequestHeader) struct;
            contextId = h.contextId;
            name = getNameFor(h);
            details = String.format("from %d.%d, requestId=%d", h.fromParentId, h.fromChildId, h.requestId);
            break;
         }
         case RESPONSE: {
            ResponseHeader h = (ResponseHeader) struct;
            contextId = h.contextId;
            if (s != null && s.getStructId() == io.tetrapod.core.rpc.Error.STRUCT_ID) {
               io.tetrapod.core.rpc.Error e = (io.tetrapod.core.rpc.Error) s;
               name = Contract.getErrorCode(e.code, e.getContractId());
            } else {
               name = getNameFor(h);
            }
            details = String.format("requestId=%d", h.requestId);
            break;
         }
         default: {
            details = "";
         }
      }

      return String.format("%s [%016X] [%s-%d] %s %s (%s) %s", time, contextId, ses, header.sessionId, direction, name, details,
            payloadDetails);

      //return String.format("[%d] %s : %s : %s", header.timestamp, header.type, struct.dump(), ((Structure) payload).dump());
      //      System.out.println(header.timestamp + " " + header.type + " : " + struct.dump());
      //      if (payload instanceof Structure) {
      //         System.out.println("\t" + ((Structure) payload).dump());
      //      }
   }
}
