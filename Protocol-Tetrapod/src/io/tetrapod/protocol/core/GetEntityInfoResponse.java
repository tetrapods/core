package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import io.tetrapod.protocol.core.TypeDescriptor;
import io.tetrapod.protocol.core.StructDescription;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class GetEntityInfoResponse extends Response {
   
   public static final int STRUCT_ID = 11007413;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
    
   public GetEntityInfoResponse() {
      defaults();
   }

   public GetEntityInfoResponse(int build, String name, String host, String referrer) {
      this.build = build;
      this.name = name;
      this.host = host;
      this.referrer = referrer;
   }   
   
   public int build;
   public String name;
   public String host;
   public String referrer;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      build = 0;
      name = null;
      host = null;
      referrer = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.build);
      data.write(2, this.name);
      data.write(3, this.host);
      data.write(4, this.referrer);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.build = data.read_int(tag); break;
            case 2: this.name = data.read_string(tag); break;
            case 3: this.host = data.read_string(tag); break;
            case 4: this.referrer = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return GetEntityInfoResponse.CONTRACT_ID;
   }

   public final int getStructId() {
      return GetEntityInfoResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[4+1];
      result[1] = "build";
      result[2] = "name";
      result[3] = "host";
      result[4] = "referrer";
      return result;
   }

   public final Structure make() {
      return new GetEntityInfoResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "GetEntityInfoResponse";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }
 }
