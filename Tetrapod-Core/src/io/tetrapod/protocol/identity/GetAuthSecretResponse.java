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
public class GetAuthSecretResponse extends Response {
   
   public static final int STRUCT_ID = 378568;
   public static final int CONTRACT_ID = IdentityContract.CONTRACT_ID;
    
   public GetAuthSecretResponse() {
      defaults();
   }

   public GetAuthSecretResponse(byte[] secret) {
      this.secret = secret;
   }   
   
   public byte[] secret;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      secret = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      if (this.secret != null) data.write(1, this.secret);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.secret = data.read_byte_array(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return GetAuthSecretResponse.CONTRACT_ID;
   }

   public final int getStructId() {
      return GetAuthSecretResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[1+1];
      result[1] = "secret";
      return result;
   }

   public final Structure make() {
      return new GetAuthSecretResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_BYTE_LIST, 0, 0);
      return desc;
   }
 }
