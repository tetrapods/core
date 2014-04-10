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

@SuppressWarnings("unused")
public class AddUnverifiedUserResponse extends Response {
   
   public static final int STRUCT_ID = 415825;
   public static final int CONTRACT_ID = IdentityContract.CONTRACT_ID;
    
   public AddUnverifiedUserResponse() {
      defaults();
   }

   public AddUnverifiedUserResponse(int accountId, String username, boolean isNewUser) {
      this.accountId = accountId;
      this.username = username;
      this.isNewUser = isNewUser;
   }   
   
   public int accountId;
   
   /**
    * if the user existed this is potentially different from the passed in username
    */
   public String username;
   public boolean isNewUser;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      accountId = 0;
      username = null;
      isNewUser = false;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.accountId);
      data.write(2, this.username);
      data.write(3, this.isNewUser);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.accountId = data.read_int(tag); break;
            case 2: this.username = data.read_string(tag); break;
            case 3: this.isNewUser = data.read_boolean(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return AddUnverifiedUserResponse.CONTRACT_ID;
   }

   public final int getStructId() {
      return AddUnverifiedUserResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "accountId";
      result[2] = "username";
      result[3] = "isNewUser";
      return result;
   }

   public final Structure make() {
      return new AddUnverifiedUserResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      return desc;
   }
 }
