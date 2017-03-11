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
public class ReleaseOwnershipRequest extends Request  {

   public static final int STRUCT_ID = 3927214;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = TetrapodContract.SUB_CONTRACT_ID;

   public ReleaseOwnershipRequest() {
      defaults();
   }

   public ReleaseOwnershipRequest(String prefix, String[] keys) {
      this.prefix = prefix;
      this.keys = keys;
   }   

   public String prefix;
   
   /**
    * pass null for ALL
    */
   public String[] keys;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      prefix = null;
      keys = null;
   }

   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.prefix);
      if (this.keys != null) data.write(2, this.keys);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.prefix = data.read_string(tag); break;
            case 2: this.keys = data.read_string_array(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return ReleaseOwnershipRequest.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return ReleaseOwnershipRequest.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return ReleaseOwnershipRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestReleaseOwnership(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestReleaseOwnership(ReleaseOwnershipRequest r, RequestContext ctx);
   }

   public static interface Handler2 {
      @RequestClass(ReleaseOwnershipRequest.class)
      Task<Response> releaseOwnership(String prefix, String[] keys);
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "prefix";
      result[2] = "keys";
      return result;
   }
   
   public final Structure make() {
      return new ReleaseOwnershipRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "ReleaseOwnershipRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING_LIST, 0, 0);
      return desc;
   }

}
