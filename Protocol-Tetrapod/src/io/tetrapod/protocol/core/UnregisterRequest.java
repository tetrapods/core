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
public class UnregisterRequest extends Request {

   public static final int STRUCT_ID = 3896262;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   
   public UnregisterRequest() {
      defaults();
   }

   public UnregisterRequest(int accountId, String authToken, int entityId) {
      this.accountId = accountId;
      this.authToken = authToken;
      this.entityId = entityId;
   }   

   public int accountId;
   public String authToken;
   public int entityId;

   public final Structure.Security getSecurity() {
      return Security.ADMIN;
   }

   public final void defaults() {
      accountId = 0;
      authToken = null;
      entityId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.accountId);
      data.write(2, this.authToken);
      data.write(3, this.entityId);
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
            case 3: this.entityId = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return UnregisterRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return UnregisterRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestUnregister(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestUnregister(UnregisterRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "accountId";
      result[2] = "authToken";
      result[3] = "entityId";
      return result;
   }
   
   public final Structure make() {
      return new UnregisterRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "UnregisterRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }

   public final Response securityCheck(RequestContext ctx) {
      return ctx.securityCheck(this, accountId, authToken, Admin.RIGHTS_CLUSTER_WRITE);
   }
       
   protected boolean isSensitive(String fieldName) {
      if (fieldName.equals("authToken")) return true;
      return false;
   }
}
