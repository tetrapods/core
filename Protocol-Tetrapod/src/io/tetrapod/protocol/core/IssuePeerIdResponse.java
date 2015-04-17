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
public class IssuePeerIdResponse extends Response {
   
   public static final int STRUCT_ID = 14036188;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
    
   public IssuePeerIdResponse() {
      defaults();
   }

   public IssuePeerIdResponse(int peerId, int entityId, long term, long index) {
      this.peerId = peerId;
      this.entityId = entityId;
      this.term = term;
      this.index = index;
   }   
   
   /**
    * our new peerId
    */
   public int peerId;
   
   /**
    * of the host we are talking to
    */
   public int entityId;
   
   /**
    * term of command that issued the peerId
    */
   public long term;
   
   /**
    * index of command that issued the peerId
    */
   public long index;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      peerId = 0;
      entityId = 0;
      term = 0;
      index = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.peerId);
      data.write(2, this.entityId);
      data.write(3, this.term);
      data.write(4, this.index);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.peerId = data.read_int(tag); break;
            case 2: this.entityId = data.read_int(tag); break;
            case 3: this.term = data.read_long(tag); break;
            case 4: this.index = data.read_long(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return IssuePeerIdResponse.CONTRACT_ID;
   }

   public final int getStructId() {
      return IssuePeerIdResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[4+1];
      result[1] = "peerId";
      result[2] = "entityId";
      result[3] = "term";
      result[4] = "index";
      return result;
   }

   public final Structure make() {
      return new IssuePeerIdResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      return desc;
   }
 }
