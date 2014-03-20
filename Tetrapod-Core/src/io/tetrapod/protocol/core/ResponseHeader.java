package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class ResponseHeader extends Structure {
   
   public static final int STRUCT_ID = 675609;
    
   public ResponseHeader() {
      defaults();
   }

   public ResponseHeader(int requestId, int structId, int toId, int contractId) {
      this.requestId = requestId;
      this.structId = structId;
      this.toId = toId;
      this.contractId = contractId;
   }   
   
   public int requestId;
   public int structId;
   public int toId;
   public int contractId;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

  public final void defaults() {
      requestId = 0;
      structId = 0;
      toId = 0;
      contractId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.requestId);
      data.write(2, this.structId);
      data.write(3, this.toId);
      data.write(4, this.contractId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.requestId = data.read_int(tag); break;
            case 2: this.structId = data.read_int(tag); break;
            case 3: this.toId = data.read_int(tag); break;
            case 4: this.contractId = data.read_int(tag); break;
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
      return ResponseHeader.STRUCT_ID;
   }
   
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new ResponseHeader(); }
      };
   }
}
