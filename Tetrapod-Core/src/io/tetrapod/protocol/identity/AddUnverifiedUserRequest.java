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
 * Adds a user with the given identity, or returns the user if it already exists.  The verify portion of the identity is ignored. A newly created user will start with PROPS_NO_PASSWORD set.
 */

@SuppressWarnings("unused")
public class AddUnverifiedUserRequest extends Request {

   public static final int STRUCT_ID = 3799907;
   public static final int CONTRACT_ID = IdentityContract.CONTRACT_ID;
   
   public AddUnverifiedUserRequest() {
      defaults();
   }

   public AddUnverifiedUserRequest(Identity identity, String username) {
      this.identity = identity;
      this.username = username;
   }   

   public Identity identity;
   public String username;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      identity = null;
      username = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      if (this.identity != null) data.write(1, this.identity);
      data.write(2, this.username);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.identity = data.read_struct(tag, new Identity()); break;
            case 2: this.username = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return AddUnverifiedUserRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return AddUnverifiedUserRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestAddUnverifiedUser(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestAddUnverifiedUser(AddUnverifiedUserRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "identity";
      result[2] = "username";
      return result;
   }
   
   public final Structure make() {
      return new AddUnverifiedUserRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRUCT, Identity.CONTRACT_ID, Identity.STRUCT_ID);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

}
