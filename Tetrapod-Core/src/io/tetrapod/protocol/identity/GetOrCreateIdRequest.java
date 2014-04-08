package io.tetrapod.protocol.identity;

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
public class GetOrCreateIdRequest extends Request {

   public static final int STRUCT_ID = 4704002;
   public static final int CONTRACT_ID = IdentityContract.CONTRACT_ID;
   
   public GetOrCreateIdRequest() {
      defaults();
   }

   public GetOrCreateIdRequest(String email, String username) {
      this.email = email;
      this.username = username;
   }   

   public String email;
   public String username;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      email = null;
      username = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.email);
      data.write(2, this.username);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.email = data.read_string(tag); break;
            case 2: this.username = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return GetOrCreateIdRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return GetOrCreateIdRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestGetOrCreateId(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestGetOrCreateId(GetOrCreateIdRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "email";
      result[2] = "username";
      return result;
   }
   
   public final Structure make() {
      return new GetOrCreateIdRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

}
