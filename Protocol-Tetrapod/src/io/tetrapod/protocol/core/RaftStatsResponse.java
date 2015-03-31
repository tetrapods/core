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
public class RaftStatsResponse extends Response {
   
   public static final int STRUCT_ID = 13186680;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
    
   public RaftStatsResponse() {
      defaults();
   }

   public RaftStatsResponse(byte role, long curTerm, long lastTerm, long lastIndex, long commitIndex, int[] peers) {
      this.role = role;
      this.curTerm = curTerm;
      this.lastTerm = lastTerm;
      this.lastIndex = lastIndex;
      this.commitIndex = commitIndex;
      this.peers = peers;
   }   
   
   public byte role;
   public long curTerm;
   public long lastTerm;
   public long lastIndex;
   public long commitIndex;
   public int[] peers;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      role = 0;
      curTerm = 0;
      lastTerm = 0;
      lastIndex = 0;
      commitIndex = 0;
      peers = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.role);
      data.write(2, this.curTerm);
      data.write(3, this.lastTerm);
      data.write(4, this.lastIndex);
      data.write(5, this.commitIndex);
      if (this.peers != null) data.write(6, this.peers);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.role = data.read_byte(tag); break;
            case 2: this.curTerm = data.read_long(tag); break;
            case 3: this.lastTerm = data.read_long(tag); break;
            case 4: this.lastIndex = data.read_long(tag); break;
            case 5: this.commitIndex = data.read_long(tag); break;
            case 6: this.peers = data.read_int_array(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return RaftStatsResponse.CONTRACT_ID;
   }

   public final int getStructId() {
      return RaftStatsResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[6+1];
      result[1] = "role";
      result[2] = "curTerm";
      result[3] = "lastTerm";
      result[4] = "lastIndex";
      result[5] = "commitIndex";
      result[6] = "peers";
      return result;
   }

   public final Structure make() {
      return new RaftStatsResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_BYTE, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[6] = new TypeDescriptor(TypeDescriptor.T_INT_LIST, 0, 0);
      return desc;
   }
 }
