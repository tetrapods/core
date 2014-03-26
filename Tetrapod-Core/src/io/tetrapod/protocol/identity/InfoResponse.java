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

   public InfoResponse(String username, int properties) {
      this.username = username;
      this.properties = properties;
   }   
   
   public String username;
   public int properties;

   public final Structure.Security getSecurity() {
      return Security.PROTECTED;
   }

   public final void defaults() {
      username = null;
      properties = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.username);
      data.write(2, this.properties);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.username = data.read_string(tag); break;
            case 2: this.properties = data.read_int(tag); break;
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
      
   public final int getContractId() {
      return IdentityContract.CONTRACT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "username";
      result[2] = "properties";
      return result;
   }

   public final Structure make() {
      return new InfoResponse();
   }
}
