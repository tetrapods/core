package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * a request from the web as an uninterpreted json
 */

@SuppressWarnings("unused")
public class WrappedJSON extends Structure {
   
   public static final int STRUCT_ID = 1917218;
    
   public WrappedJSON() {
      defaults();
   }

   public WrappedJSON(String json) {
      this.json = json;
   }   
   
   public String json;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      json = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.json);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.json = data.read_string(tag); break;
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
      return WrappedJSON.STRUCT_ID;
   }
   
   public final int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[1+1];
      result[1] = "json";
      return result;
   }

   public final Structure make() {
      return new WrappedJSON();
   }
}
