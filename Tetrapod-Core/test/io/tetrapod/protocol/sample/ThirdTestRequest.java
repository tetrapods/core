package io.tetrapod.protocol.sample;

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
public class ThirdTestRequest extends Request {

   public static final int STRUCT_ID = 14473781;
   public static final int CONTRACT_ID = SampleContract.CONTRACT_ID;
   
   public ThirdTestRequest() {
      defaults();
   }

   public ThirdTestRequest(int accountId, String token) {
      this.accountId = accountId;
      this.token = token;
   }   

   public int accountId;
   public String token;

   public final Structure.Security getSecurity() {
      return Security.PROTECTED;
   }

   public final void defaults() {
      accountId = 0;
      token = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.accountId);
      data.write(2, this.token);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.accountId = data.read_int(tag); break;
            case 2: this.token = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return ThirdTestRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return ThirdTestRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestThirdTest(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestThirdTest(ThirdTestRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "accountId";
      result[2] = "token";
      return result;
   }
   
   public final Structure make() {
      return new ThirdTestRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

   public final Response securityCheck(RequestContext ctx) {
      return super.securityCheck(accountId, token, ctx);
   }
      
}
