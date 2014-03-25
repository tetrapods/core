package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class RequestHeader extends Structure {
   
   public static final int STRUCT_ID = 7165109;
    
   public RequestHeader() {
      defaults();
   }

   public RequestHeader(int requestId, int fromId, int toId, byte fromType, byte timeout, int version, int structId, int contractId) {
      this.requestId = requestId;
      this.fromId = fromId;
      this.toId = toId;
      this.fromType = fromType;
      this.timeout = timeout;
      this.version = version;
      this.structId = structId;
      this.contractId = contractId;
   }   
   
   public int requestId;
   public int fromId;
   public int toId;
   public byte fromType;
   public byte timeout;
   public int version;
   public int structId;
   public int contractId;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      requestId = 0;
      fromId = 0;
      toId = 0;
      fromType = 0;
      timeout = 0;
      version = 0;
      structId = 0;
      contractId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.requestId);
      data.write(2, this.fromId);
      data.write(3, this.toId);
      data.write(4, this.fromType);
      data.write(5, this.timeout);
      data.write(6, this.version);
      data.write(7, this.structId);
      data.write(8, this.contractId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.requestId = data.read_int(tag); break;
            case 2: this.fromId = data.read_int(tag); break;
            case 3: this.toId = data.read_int(tag); break;
            case 4: this.fromType = data.read_byte(tag); break;
            case 5: this.timeout = data.read_byte(tag); break;
            case 6: this.version = data.read_int(tag); break;
            case 7: this.structId = data.read_int(tag); break;
            case 8: this.contractId = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   @Override
   public final int getStructId() {
      return RequestHeader.STRUCT_ID;
   }
   
   public final int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }

   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new RequestHeader(); }
      };
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[8+1];
      result[1] = "requestId";
      result[2] = "fromId";
      result[3] = "toId";
      result[4] = "fromType";
      result[5] = "timeout";
      result[6] = "version";
      result[7] = "structId";
      result[8] = "contractId";
      return result;
   }
}
