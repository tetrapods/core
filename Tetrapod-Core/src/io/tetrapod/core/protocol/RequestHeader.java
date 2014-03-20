package io.tetrapod.core.protocol;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class RequestHeader extends Structure {
   
   /**
    * request is sent to service on other end of socket
    */
   public static final int TO_ID_DIRECT = 1; 
   
   /**
    * request is sent to any service matching the contract id
    */
   public static final int TO_ID_SERVICE = 2; 
   
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
   
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new RequestHeader(); }
      };
   }
}
