package io.tetrapod.protocol.identity;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class InfoResponse extends Response {
   
   public static final int STRUCT_ID = 3624488;
    
   public InfoResponse() {
      defaults();
   }

   public InfoResponse(String username) {
      this.username = username;
   }   
   
   public String username;

   public final Structure.Security getSecurity() {
      return Security.PROTECTED;
   }

   public final void defaults() {
      username = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.username);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.username = data.read_string(tag); break;
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
      return InfoResponse.STRUCT_ID;
   }
      
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new InfoResponse(); }
      };
   }
}
