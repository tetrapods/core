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
public class AdminResetPasswordRequest extends Request  {

   public static final int STRUCT_ID = 868729;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = TetrapodContract.SUB_CONTRACT_ID;

   public AdminResetPasswordRequest() {
      defaults();
   }

   public AdminResetPasswordRequest(int accountId, String authToken, int targetAccountId, String password) {
      this.accountId = accountId;
      this.authToken = authToken;
      this.targetAccountId = targetAccountId;
      this.password = password;
   }   

   public int accountId;
   public String authToken;
   public int targetAccountId;
   public String password;

   public final Structure.Security getSecurity() {
      return Security.ADMIN;
   }

   public final void defaults() {
      accountId = 0;
      authToken = null;
      targetAccountId = 0;
      password = null;
   }

   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.accountId);
      data.write(2, this.authToken);
      data.write(3, this.targetAccountId);
      data.write(4, this.password);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.accountId = data.read_int(tag); break;
            case 2: this.authToken = data.read_string(tag); break;
            case 3: this.targetAccountId = data.read_int(tag); break;
            case 4: this.password = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return AdminResetPasswordRequest.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return AdminResetPasswordRequest.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return AdminResetPasswordRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestAdminResetPassword(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestAdminResetPassword(AdminResetPasswordRequest r, RequestContext ctx);
   }

   public static interface Handler2 {
      @RequestClass(AdminResetPasswordRequest.class)
      Task<Response> adminResetPassword(int accountId, String authToken, int targetAccountId, String password);
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[4+1];
      result[1] = "accountId";
      result[2] = "authToken";
      result[3] = "targetAccountId";
      result[4] = "password";
      return result;
   }
   
   public final Structure make() {
      return new AdminResetPasswordRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "AdminResetPasswordRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

   public final Response securityCheck(RequestContext ctx) {
      return ctx.securityCheck(this, accountId, authToken, Admin.RIGHTS_USER_WRITE);
   }
       
   @Override
   public boolean isSensitive(String fieldName) {
      if (fieldName.equals("authToken")) return true;
      if (fieldName.equals("password")) return true;
      return false;
   }
}
