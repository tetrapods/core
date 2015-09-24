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
public class DelClusterPropertyRequest extends Request {

   public static final int STRUCT_ID = 15970020;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   
   public DelClusterPropertyRequest() {
      defaults();
   }

   public DelClusterPropertyRequest(String adminToken, String key) {
      this.adminToken = adminToken;
      this.key = key;
   }   

   public String adminToken;
   public String key;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      adminToken = null;
      key = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.adminToken);
      data.write(2, this.key);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.adminToken = data.read_string(tag); break;
            case 2: this.key = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return DelClusterPropertyRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return DelClusterPropertyRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestDelClusterProperty(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestDelClusterProperty(DelClusterPropertyRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "adminToken";
      result[2] = "key";
      return result;
   }
   
   public final Structure make() {
      return new DelClusterPropertyRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "DelClusterPropertyRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

   protected boolean isSensitive(String fieldName) {
      if (fieldName.equals("adminToken")) return true;
      return false;
   }
}
