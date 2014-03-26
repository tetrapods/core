package io.tetrapod.protocol.identity;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class CreateRequest extends Request {

   public static final int STRUCT_ID = 6552804;
   
   public CreateRequest() {
      defaults();
   }

   public CreateRequest(String username, String email, String password) {
      this.username = username;
      this.email = email;
      this.password = password;
   }   

   public String username;
   public String email;
   public String password;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      username = null;
      email = null;
      password = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.username);
      data.write(2, this.email);
      data.write(3, this.password);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.username = data.read_string(tag); break;
            case 2: this.email = data.read_string(tag); break;
            case 3: this.password = data.read_string(tag); break;
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
      return CreateRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestCreate(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestCreate(CreateRequest r, RequestContext ctx);
   }
   
   public final int getContractId() {
      return IdentityContract.CONTRACT_ID;
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "username";
      result[2] = "email";
      result[3] = "password";
      return result;
   }
   
   public final Structure make() {
      return new CreateRequest();
   }
}
