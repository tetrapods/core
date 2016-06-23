package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.Admin;
import io.tetrapod.core.serialize.*;
import io.tetrapod.protocol.core.TypeDescriptor;
import io.tetrapod.protocol.core.StructDescription;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("all")
public class ClaimOwnershipRequest extends RequestWithResponse<ClaimOwnershipResponse> {

   public static final int STRUCT_ID = 4158859;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   
   public ClaimOwnershipRequest() {
      defaults();
   }

   public ClaimOwnershipRequest(String prefix, String key, int leaseMillis) {
      this.prefix = prefix;
      this.key = key;
      this.leaseMillis = leaseMillis;
   }   

   public String prefix;
   public String key;
   public int leaseMillis;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      prefix = null;
      key = null;
      leaseMillis = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.prefix);
      data.write(2, this.key);
      data.write(3, this.leaseMillis);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.prefix = data.read_string(tag); break;
            case 2: this.key = data.read_string(tag); break;
            case 3: this.leaseMillis = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return ClaimOwnershipRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return ClaimOwnershipRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestClaimOwnership(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestClaimOwnership(ClaimOwnershipRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "prefix";
      result[2] = "key";
      result[3] = "leaseMillis";
      return result;
   }
   
   public final Structure make() {
      return new ClaimOwnershipRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "ClaimOwnershipRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }

}
