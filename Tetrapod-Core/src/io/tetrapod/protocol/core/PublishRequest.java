package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class PublishRequest extends Request {

   public static final int STRUCT_ID = 3171651;
   
   @ERR public static final int ERROR_INVALID_ENTITY = TetrapodContract.ERROR_INVALID_ENTITY; 
   @ERR public static final int ERROR_NOT_PARENT = TetrapodContract.ERROR_NOT_PARENT; 
   @ERR public static final int ERROR_NOT_READY = TetrapodContract.ERROR_NOT_READY; 
      
   public PublishRequest() {
      defaults();
   }

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            
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
      return PublishRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestPublish(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestPublish(PublishRequest r, RequestContext ctx);
   }
   
   public final int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[0+1];
      
      return result;
   }
   
   public final Structure make() {
      return new PublishRequest();
   }
}
