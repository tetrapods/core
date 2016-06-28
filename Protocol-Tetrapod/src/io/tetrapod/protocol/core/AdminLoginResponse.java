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
public class AdminLoginResponse extends Response {
   
   public static final int STRUCT_ID = 4213436;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = TetrapodContract.SUB_CONTRACT_ID;

   public AdminLoginResponse() {
      defaults();
   }

   public AdminLoginResponse(String token, int accountId) {
      this.token = token;
      this.accountId = accountId;
   }   
   
   public String token;
   public int accountId;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      token = null;
      accountId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.token);
      data.write(2, this.accountId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.token = data.read_string(tag); break;
            case 2: this.accountId = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return AdminLoginResponse.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return AdminLoginResponse.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return AdminLoginResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "token";
      result[2] = "accountId";
      return result;
   }

   public final Structure make() {
      return new AdminLoginResponse();
   }

   protected boolean isSensitive(String fieldName) {
      if (fieldName.equals("token")) return true;
      return false;
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "AdminLoginResponse";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }
 }
