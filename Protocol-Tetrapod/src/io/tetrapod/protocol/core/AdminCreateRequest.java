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
public class AdminCreateRequest extends Request {

   public static final int STRUCT_ID = 14596683;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   
   public AdminCreateRequest() {
      defaults();
   }

   public AdminCreateRequest(String token, String email, String password, long rights) {
      this.token = token;
      this.email = email;
      this.password = password;
      this.rights = rights;
   }   

   public String token;
   public String email;
   public String password;
   public long rights;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      token = null;
      email = null;
      password = null;
      rights = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.token);
      data.write(2, this.email);
      data.write(3, this.password);
      data.write(4, this.rights);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.token = data.read_string(tag); break;
            case 2: this.email = data.read_string(tag); break;
            case 3: this.password = data.read_string(tag); break;
            case 4: this.rights = data.read_long(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return AdminCreateRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return AdminCreateRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestAdminCreate(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestAdminCreate(AdminCreateRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[4+1];
      result[1] = "token";
      result[2] = "email";
      result[3] = "password";
      result[4] = "rights";
      return result;
   }
   
   public final Structure make() {
      return new AdminCreateRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      return desc;
   }

   protected boolean isSensitive(String fieldName) {
      if (fieldName.equals("token")) return true;
      if (fieldName.equals("email")) return true;
      if (fieldName.equals("password")) return true;
      return false;
   }
}
