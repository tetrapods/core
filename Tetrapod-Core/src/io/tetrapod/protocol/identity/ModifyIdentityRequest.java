package io.tetrapod.protocol.identity;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import io.tetrapod.protocol.core.TypeDescriptor;
import io.tetrapod.protocol.core.StructDescription;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Modifies the verification part of an identity.  Not every identity can do this.
 */

@SuppressWarnings("unused")
public class ModifyIdentityRequest extends Request {

   public static final int STRUCT_ID = 7124244;
   public static final int CONTRACT_ID = IdentityContract.CONTRACT_ID;
   
   public ModifyIdentityRequest() {
      defaults();
   }

   public ModifyIdentityRequest(int accountId, String authToken, Identity oldValue, Identity updatedValue, String username) {
      this.accountId = accountId;
      this.authToken = authToken;
      this.oldValue = oldValue;
      this.updatedValue = updatedValue;
      this.username = username;
   }   

   public int accountId;
   public String authToken;
   public Identity oldValue;
   public Identity updatedValue;
   public String username;

   public final Structure.Security getSecurity() {
      return Security.PROTECTED;
   }

   public final void defaults() {
      accountId = 0;
      authToken = null;
      oldValue = null;
      updatedValue = null;
      username = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.accountId);
      data.write(2, this.authToken);
      if (this.oldValue != null) data.write(3, this.oldValue);
      if (this.updatedValue != null) data.write(4, this.updatedValue);
      data.write(5, this.username);
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
            case 3: this.oldValue = data.read_struct(tag, new Identity()); break;
            case 4: this.updatedValue = data.read_struct(tag, new Identity()); break;
            case 5: this.username = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return ModifyIdentityRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return ModifyIdentityRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestModifyIdentity(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestModifyIdentity(ModifyIdentityRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[5+1];
      result[1] = "accountId";
      result[2] = "authToken";
      result[3] = "oldValue";
      result[4] = "updatedValue";
      result[5] = "username";
      return result;
   }
   
   public final Structure make() {
      return new ModifyIdentityRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRUCT, Identity.CONTRACT_ID, Identity.STRUCT_ID);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_STRUCT, Identity.CONTRACT_ID, Identity.STRUCT_ID);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

   public final Response securityCheck(RequestContext ctx) {
      return super.securityCheck(accountId, authToken, ctx);
   }
      
}
