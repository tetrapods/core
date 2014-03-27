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
public class MessageHeader extends Structure {
   
   public static final int STRUCT_ID = 11760427;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
    
   public MessageHeader() {
      defaults();
   }

   public MessageHeader(int fromId, int topicId, int toId, int contractId, int structId) {
      this.fromId = fromId;
      this.topicId = topicId;
      this.toId = toId;
      this.contractId = contractId;
      this.structId = structId;
   }   
   
   public int fromId;
   public int topicId;
   public int toId;
   public int contractId;
   public int structId;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      fromId = 0;
      topicId = 0;
      toId = 0;
      contractId = 0;
      structId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.fromId);
      data.write(2, this.topicId);
      data.write(3, this.toId);
      data.write(4, this.contractId);
      data.write(5, this.structId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.fromId = data.read_int(tag); break;
            case 2: this.topicId = data.read_int(tag); break;
            case 3: this.toId = data.read_int(tag); break;
            case 4: this.contractId = data.read_int(tag); break;
            case 5: this.structId = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return MessageHeader.CONTRACT_ID;
   }

   public final int getStructId() {
      return MessageHeader.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[5+1];
      result[1] = "fromId";
      result[2] = "topicId";
      result[3] = "toId";
      result[4] = "contractId";
      result[5] = "structId";
      return result;
   }

   public final Structure make() {
      return new MessageHeader();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }
}
