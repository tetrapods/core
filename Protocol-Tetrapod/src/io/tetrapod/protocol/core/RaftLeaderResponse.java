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
public class RaftLeaderResponse extends Response {
   
   public static final int STRUCT_ID = 10320426;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = TetrapodContract.SUB_CONTRACT_ID;

   public RaftLeaderResponse() {
      defaults();
   }

   public RaftLeaderResponse(ServerAddress leader) {
      this.leader = leader;
   }   
   
   public ServerAddress leader;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      leader = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      if (this.leader != null) data.write(1, this.leader);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.leader = data.read_struct(tag, new ServerAddress()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return RaftLeaderResponse.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return RaftLeaderResponse.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return RaftLeaderResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[1+1];
      result[1] = "leader";
      return result;
   }

   public final Structure make() {
      return new RaftLeaderResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "RaftLeaderResponse";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRUCT, ServerAddress.CONTRACT_ID, ServerAddress.STRUCT_ID);
      return desc;
   }
 }
