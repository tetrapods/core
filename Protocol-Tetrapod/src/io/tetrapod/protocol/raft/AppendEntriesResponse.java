package io.tetrapod.protocol.raft;

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
public class AppendEntriesResponse extends Response {
   
   public static final int STRUCT_ID = 32510;
   public static final int CONTRACT_ID = RaftContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = RaftContract.SUB_CONTRACT_ID;

   public AppendEntriesResponse() {
      defaults();
   }

   public AppendEntriesResponse(long term, boolean success, long lastLogIndex) {
      this.term = term;
      this.success = success;
      this.lastLogIndex = lastLogIndex;
   }   
   
   public long term;
   public boolean success;
   public long lastLogIndex;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      term = 0;
      success = false;
      lastLogIndex = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.term);
      data.write(2, this.success);
      data.write(3, this.lastLogIndex);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.term = data.read_long(tag); break;
            case 2: this.success = data.read_boolean(tag); break;
            case 3: this.lastLogIndex = data.read_long(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return AppendEntriesResponse.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return AppendEntriesResponse.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return AppendEntriesResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "term";
      result[2] = "success";
      result[3] = "lastLogIndex";
      return result;
   }

   public final Structure make() {
      return new AppendEntriesResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "AppendEntriesResponse";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      return desc;
   }
 }
