package io.tetrapod.protocol.web;

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
public class RegisterRequest extends Request {

   public static final int STRUCT_ID = 10895179;
   public static final int CONTRACT_ID = WebContract.CONTRACT_ID;
   
   public RegisterRequest() {
      defaults();
   }

   public RegisterRequest(String name, String build, String host) {
      this.name = name;
      this.build = build;
      this.host = host;
   }   

   public String name;
   public String build;
   public String host;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      name = null;
      build = null;
      host = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.name);
      data.write(2, this.build);
      data.write(3, this.host);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.name = data.read_string(tag); break;
            case 2: this.build = data.read_string(tag); break;
            case 3: this.host = data.read_string(tag); break;
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
      String[] result = new String[3+1];
      result[1] = "name";
      result[2] = "build";
      result[3] = "host";
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
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

}
