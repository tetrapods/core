package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class RegisterRequest extends Request {

   public static final int STRUCT_ID = 10895179;
   
   public RegisterRequest() {
      defaults();
   }

   public RegisterRequest(int build, String token, int contractId, String name) {
      this.build = build;
      this.token = token;
      this.contractId = contractId;
      this.name = name;
   }   

   public int build;
   public String token;
   public int contractId;
   public String name;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      build = 0;
      token = null;
      contractId = 0;
      name = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.build);
      data.write(2, this.token);
      data.write(3, this.contractId);
      data.write(4, this.name);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.build = data.read_int(tag); break;
            case 2: this.token = data.read_string(tag); break;
            case 3: this.contractId = data.read_int(tag); break;
            case 4: this.name = data.read_string(tag); break;
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
      return RegisterRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestRegister(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestRegister(RegisterRequest r, RequestContext ctx);
   }
   
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new RegisterRequest(); }
      };
   }
   
   public final int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[4+1];
      result[1] = "build";
      result[2] = "token";
      result[3] = "contractId";
      result[4] = "name";
      return result;
   }
   
}
