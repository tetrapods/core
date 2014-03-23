package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class PublishResponse extends Response {
   
   public static final int STRUCT_ID = 2698673;
    
   public PublishResponse() {
      defaults();
   }

   public PublishResponse(int topicId) {
      this.topicId = topicId;
   }   
   
   public int topicId;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      topicId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.topicId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.topicId = data.read_int(tag); break;
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
      return PublishResponse.STRUCT_ID;
   }
      
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new PublishResponse(); }
      };
   }
      
   public final int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }
}
