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

@SuppressWarnings("unused")
public class DirectConnectionResponse extends Response {
   
   public static final int STRUCT_ID = 16162197;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
    
   public DirectConnectionResponse() {
      defaults();
   }

   public DirectConnectionResponse(ServerAddress address, String token) {
      this.address = address;
      this.token = token;
   }   
   
   public ServerAddress address;
   public String token;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      address = null;
      token = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      if (this.address != null) data.write(1, this.address);
      data.write(2, this.token);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.address = data.read_struct(tag, new ServerAddress()); break;
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
      return DirectConnectionResponse.CONTRACT_ID;
   }

   public final int getStructId() {
      return DirectConnectionResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "address";
      result[2] = "token";
      return result;
   }

   public final Structure make() {
      return new DirectConnectionResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRUCT, ServerAddress.CONTRACT_ID, ServerAddress.STRUCT_ID);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }
 }
