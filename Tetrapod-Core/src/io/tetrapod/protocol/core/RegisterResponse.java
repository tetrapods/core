package io.tetrapod.protocol.core;

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

   public RegisterResponse(int entityId, int parentId, String token) {
      this.entityId = entityId;
      this.parentId = parentId;
      this.token = token;
   }   
   
   public int entityId;
   public int parentId;
   public String token;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      entityId = 0;
      parentId = 0;
      token = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.entityId);
      data.write(2, this.parentId);
      data.write(3, this.token);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.entityId = data.read_int(tag); break;
            case 2: this.parentId = data.read_int(tag); break;
            case 3: this.token = data.read_string(tag); break;
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
      
   public final int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "entityId";
      result[2] = "parentId";
      result[3] = "token";
      return result;
   }
}
