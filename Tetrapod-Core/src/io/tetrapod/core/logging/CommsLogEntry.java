package io.tetrapod.core.logging;

import java.io.*;

import io.tetrapod.core.StructureFactory;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.StructureAdapter;
import io.tetrapod.core.serialize.datasources.*;
import io.tetrapod.protocol.core.*;

public class CommsLogEntry {

   public final CommsLogHeader header;
   public final Structure      struct;
   public final Structure      payload;

   public CommsLogEntry(CommsLogHeader header, Structure struct, Structure payload) {
      assert header != null;
      assert struct != null;
      assert payload != null;

      this.header = header;
      this.struct = struct;
      this.payload = payload;
      //      if (payload != null) {
      //         header.payload = (byte[]) payload.toRawForm(TempBufferDataSource.forWriting());
      //      }
   }

   private static Structure readPayload(IOStreamDataSource data, CommsLogHeader header, int contractId, int structId) throws IOException {
      if (header.def != null & !StructureFactory.has(contractId, structId)) {
         StructureFactory.add(new StructureAdapter(header.def));
      }
      Structure payload = StructureFactory.make(contractId, structId);
      assert payload != null;
      //payload.read(TempBufferDataSource.forReading(header.payload));
      payload.read(data);
      return payload;
   }

   public static CommsLogEntry read(DataInputStream in) throws IOException {
      IOStreamDataSource data = IOStreamDataSource.forReading(in);
      CommsLogHeader header = new CommsLogHeader();
      header.read(data);

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

      // FIXME sanitize payloads on write, based on @sensitive tags
      IOStreamDataSource data = IOStreamDataSource.forWriting(out);
      header.write(data);
      struct.write(data);
      payload.write(data);
      System.out.println("WRITING1: " + header.timestamp + " " + header.type + " " + header.def.name);
      System.out.println("WRITING2: " + struct.dump());
      System.out.println("WRITING3: " + payload.dump());
   }

}
