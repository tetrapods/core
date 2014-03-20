package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class RelayRequest extends Request {

   public static final int STRUCT_ID = 4188601;
   
   public RelayRequest() {
      defaults();
   }

   public RelayRequest(int structId, byte[] data) {
      this.structId = structId;
      this.data = data;
   }   

   public int structId;
   public byte[] data;

   public final Structure.Security getSecurity() {
      return Security.PRIVATE;
   }

   public final void defaults() {
      structId = 0;
      data = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.structId);
      if (this.data != null) data.write(2, this.data);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.structId = data.read_int(tag); break;
            case 2: this.data = data.read_byte_array(tag); break;
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
      return RelayRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestRelay(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestRelay(RelayRequest r, RequestContext ctx);
   }
   
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new RelayRequest(); }
      };
   }
}
