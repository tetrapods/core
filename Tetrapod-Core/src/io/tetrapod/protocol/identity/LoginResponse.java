package io.tetrapod.protocol.identity;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class LoginResponse extends Response {
   
   public static final int STRUCT_ID = 16389615;
    
   public LoginResponse() {
      defaults();
   }

   public LoginResponse(int accountId, String authToken) {
      this.accountId = accountId;
      this.authToken = authToken;
   }   
   
   public int accountId;
   public String authToken;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      accountId = 0;
      authToken = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.accountId);
      data.write(2, this.authToken);
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
      return LoginResponse.STRUCT_ID;
   }
      
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new LoginResponse(); }
      };
   }
      
   public final int getContractId() {
      return IdentityContract.CONTRACT_ID;
   }
}
