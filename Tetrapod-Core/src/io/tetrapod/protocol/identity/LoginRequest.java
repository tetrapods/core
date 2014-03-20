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

   public LoginRequest(String email, String passwordHash) {
      this.email = email;
      this.passwordHash = passwordHash;
   }   

   public String email;
   public String passwordHash;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      email = null;
      passwordHash = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.email);
      data.write(2, this.passwordHash);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.email = data.read_string(tag); break;
            case 2: this.passwordHash = data.read_string(tag); break;
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
   
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new LoginRequest(); }
      };
   }
}
