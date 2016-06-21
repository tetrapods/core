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

@SuppressWarnings("all")
public class RegisterRequest extends Request<RegisterResponse> {

   public static final int STRUCT_ID = 10895179;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   
   public RegisterRequest() {
      defaults();
   }

   public RegisterRequest(String token, int contractId, String name, int status, String host, String build) {
      this.token = token;
      this.contractId = contractId;
      this.name = name;
      this.status = status;
      this.host = host;
      this.build = build;
   }   

   public String token;
   public int contractId;
   public String name;
   public int status;
   public String host;
   public String build;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      token = null;
      contractId = 0;
      name = null;
      status = 0;
      host = null;
      build = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(2, this.token);
      data.write(3, this.contractId);
      data.write(4, this.name);
      data.write(5, this.status);
      data.write(6, this.host);
      data.write(7, this.build);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 2: this.token = data.read_string(tag); break;
            case 3: this.contractId = data.read_int(tag); break;
            case 4: this.name = data.read_string(tag); break;
            case 5: this.status = data.read_int(tag); break;
            case 6: this.host = data.read_string(tag); break;
            case 7: this.build = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return RegisterRequest.CONTRACT_ID;
   }

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
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[7+1];
      result[2] = "token";
      result[3] = "contractId";
      result[4] = "name";
      result[5] = "status";
      result[6] = "host";
      result[7] = "build";
      return result;
   }
   
   public final Structure make() {
      return new RegisterRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "RegisterRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[6] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[7] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

}
