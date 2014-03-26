package io.tetrapod.protocol.identity;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class LoginRequest extends Request {

   public static final int STRUCT_ID = 8202985;
   
   @ERR public static final int ERROR_UNKNOWN_USERNAME = IdentityContract.ERROR_UNKNOWN_USERNAME; 
   @ERR public static final int ERROR_WRONG_PASSWORD = IdentityContract.ERROR_WRONG_PASSWORD; 
      
   public LoginRequest() {
      defaults();
   }

   public LoginRequest(String email, String password) {
      this.email = email;
      this.password = password;
   }   

   public String email;
   public String password;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      email = null;
      password = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.email);
      data.write(2, this.password);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.email = data.read_string(tag); break;
            case 2: this.password = data.read_string(tag); break;
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
   
   public final int getContractId() {
      return IdentityContract.CONTRACT_ID;
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "email";
      result[2] = "password";
      return result;
   }
   
   public final Structure make() {
      return new LoginRequest();
   }
}
