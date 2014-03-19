package io.tetrapod.core.protocol;

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

   public ResponseHeader(int requestId, int structId, int toId) {
      this.requestId = requestId;
      this.structId = structId;
      this.toId = toId;
   }   
   
   public int requestId;
   public int structId;
   public int toId;

   public final void defaults() {
      requestId = 0;
      structId = 0;
      toId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, requestId);
      data.write(2, structId);
      data.write(3, toId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: requestId = data.read_int(tag); break;
            case 2: structId = data.read_int(tag); break;
            case 3: toId = data.read_int(tag); break;
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
