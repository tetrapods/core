package io.tetrapod.protocol.service;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class UnpauseRequest extends Request {

   public static final int STRUCT_ID = 10620319;
   
   public UnpauseRequest() {
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
      return UnpauseRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is) {
      if (is instanceof Handler)
         return ((Handler)is).requestUnpause(this);
      return is.genericRequest(this);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestUnpause(UnpauseRequest r);
   }
   
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new UnpauseRequest(); }
      };
   }
}
