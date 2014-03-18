package io.tetrapod.core.protocol;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class RegisterResponse extends Response {
   
   public static final int STRUCT_ID = 13376201;
    
   public RegisterResponse() {
      defaults();
   }
   
   public int entityId;
   public int parentId;

   public final void defaults() {
      entityId = 0;
      parentId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, entityId);
      data.write(2, parentId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: entityId = data.read_int(tag); break;
            case 2: parentId = data.read_int(tag); break;
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
      return RegisterResponse.STRUCT_ID;
   }
      
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new RegisterResponse(); }
      };
   }
}
