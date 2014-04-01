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
 * Link always attempts to add the identity to the current accountId
 */

@SuppressWarnings("unused")
public class LinkRequest extends Request {

   public static final int STRUCT_ID = 15857496;
   public static final int CONTRACT_ID = IdentityContract.CONTRACT_ID;
   
   @ERR public static final int ERROR_VERIFICATION_ERROR = IdentityContract.ERROR_VERIFICATION_ERROR; 
      
   public LinkRequest() {
      defaults();
   }

   public LinkRequest(int accountId, String authToken, boolean overrideConflicts, Identity linkedIdentity) {
      this.accountId = accountId;
      this.authToken = authToken;
      this.overrideConflicts = overrideConflicts;
      this.linkedIdentity = linkedIdentity;
   }   

   public int accountId;
   public String authToken;
   
   /**
    * if false, a conflicting link will not succeed
    */
   public boolean overrideConflicts;
   
   /**
    * identity to add
    */
   public Identity linkedIdentity;

   public final Structure.Security getSecurity() {
      return Security.PROTECTED;
   }

   public final void defaults() {
      accountId = 0;
      authToken = null;
      overrideConflicts = false;
      linkedIdentity = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.accountId);
      data.write(2, this.authToken);
      data.write(3, this.overrideConflicts);
      if (this.linkedIdentity != null) data.write(4, this.linkedIdentity);
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
            case 3: this.overrideConflicts = data.read_boolean(tag); break;
            case 4: this.linkedIdentity = data.read_struct(tag, new Identity()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return LinkRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return LinkRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestLink(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestLink(LinkRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[4+1];
      result[1] = "accountId";
      result[2] = "authToken";
      result[3] = "overrideConflicts";
      result[4] = "linkedIdentity";
      return result;
   }
   
   public final Structure make() {
      return new LinkRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_STRUCT, Identity.CONTRACT_ID, Identity.STRUCT_ID);
      return desc;
   }

   public final Response securityCheck(RequestContext ctx) {
      return super.securityCheck(accountId, authToken, ctx);
   }
      
}
