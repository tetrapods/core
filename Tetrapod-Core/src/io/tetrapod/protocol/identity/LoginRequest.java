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
public class LoginRequest extends Request {

   public static final int STRUCT_ID = 8202985;
   public static final int CONTRACT_ID = IdentityContract.CONTRACT_ID;
   
   @ERR public static final int ERROR_UNKNOWN_USERNAME = IdentityContract.ERROR_UNKNOWN_USERNAME; 
   @ERR public static final int ERROR_VERIFICATION_FAILURE = IdentityContract.ERROR_VERIFICATION_FAILURE; 
      
   public LoginRequest() {
      defaults();
   }

   public LoginRequest(Identity ident) {
      this.ident = ident;
   }   

   public Identity ident;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      ident = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      if (this.ident != null) data.write(1, this.ident);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.ident = data.read_struct(tag, new Identity()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return LoginRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return LoginRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestLogin(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestLogin(LoginRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[1+1];
      result[1] = "ident";
      return result;
   }
   
   public final Structure make() {
      return new LoginRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRUCT, Identity.CONTRACT_ID, Identity.STRUCT_ID);
      return desc;
   }

}
