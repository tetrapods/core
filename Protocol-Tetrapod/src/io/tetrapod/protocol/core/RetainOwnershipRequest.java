package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.Admin;
import io.tetrapod.core.RequestClass;
import io.tetrapod.core.RoutedValueProvider;
import io.tetrapod.core.tasks.Task;
import io.tetrapod.core.serialize.*;
import io.tetrapod.protocol.core.TypeDescriptor;
import io.tetrapod.protocol.core.StructDescription;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("all")
public class RetainOwnershipRequest extends Request  {

   public static final int STRUCT_ID = 3539584;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = TetrapodContract.SUB_CONTRACT_ID;

   public RetainOwnershipRequest() {
      defaults();
   }

   public RetainOwnershipRequest(int leaseMillis, String prefix) {
      this.leaseMillis = leaseMillis;
      this.prefix = prefix;
   }   

   public int leaseMillis;
   public String prefix;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      leaseMillis = 0;
      prefix = null;
   }

   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.leaseMillis);
      data.write(2, this.prefix);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.leaseMillis = data.read_int(tag); break;
            case 2: this.prefix = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return RetainOwnershipRequest.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return RetainOwnershipRequest.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return RetainOwnershipRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestRetainOwnership(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestRetainOwnership(RetainOwnershipRequest r, RequestContext ctx);
   }

   public static interface Handler2 {
      @RequestClass(RetainOwnershipRequest.class)
      Task<Response> retainOwnership(int leaseMillis, String prefix);
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "leaseMillis";
      result[2] = "prefix";
      return result;
   }
   
   public final Structure make() {
      return new RetainOwnershipRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "RetainOwnershipRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

}
