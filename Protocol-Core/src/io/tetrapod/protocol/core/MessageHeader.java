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
   
   public static final byte FLAGS_ALTERNATE = 1; 
   
   public static final int STRUCT_ID = 11760427;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
    
   public MessageHeader() {
      defaults();
   }

   public MessageHeader(int fromId, int topicId, int toParentId, int toChildId, int contractId, int structId, byte flags) {
      this.fromId = fromId;
      this.topicId = topicId;
      this.toParentId = toParentId;
      this.toChildId = toChildId;
      this.contractId = contractId;
      this.structId = structId;
      this.flags = flags;
   }   
   
   public int fromId;
   public int topicId;
   public int toParentId;
   public int toChildId;
   public int contractId;
   public int structId;
   public byte flags;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      fromId = 0;
      topicId = 0;
      toParentId = 0;
      toChildId = 0;
      contractId = 0;
      structId = 0;
      flags = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.fromId);
      data.write(2, this.topicId);
      data.write(3, this.toParentId);
      data.write(4, this.toChildId);
      data.write(5, this.contractId);
      data.write(6, this.structId);
      data.write(7, this.flags);
      data.writeEndTag();
   }
   
   @SuppressWarnings("Duplicates")
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.fromId = data.read_int(tag); break;
            case 2: this.topicId = data.read_int(tag); break;
            case 3: this.toParentId = data.read_int(tag); break;
            case 4: this.toChildId = data.read_int(tag); break;
            case 5: this.contractId = data.read_int(tag); break;
            case 6: this.structId = data.read_int(tag); break;
            case 7: this.flags = data.read_byte(tag); break;
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

   @SuppressWarnings("Duplicates")
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[7+1];
      result[1] = "fromId";
      result[2] = "topicId";
      result[3] = "toParentId";
      result[4] = "toChildId";
      result[5] = "contractId";
      result[6] = "structId";
      result[7] = "flags";
      return result;
   }

   public final Structure make() {
      return new MessageHeader();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "MessageHeader";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[6] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[7] = new TypeDescriptor(TypeDescriptor.T_BYTE, 0, 0);
      return desc;
   }

   @Override
   @SuppressWarnings("RedundantIfStatement")
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      MessageHeader that = (MessageHeader) o;

      if (fromId != that.fromId)
         return false;
      if (topicId != that.topicId)
         return false;
      if (toId != that.toId)
         return false;
      if (contractId != that.contractId)
         return false;
      if (structId != that.structId)
         return false;
      if (flags != that.flags)
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = 0;
      result = 31 * result + fromId;
      result = 31 * result + topicId;
      result = 31 * result + toId;
      result = 31 * result + contractId;
      result = 31 * result + structId;
      result = 31 * result + flags;
      return result;
   }

}
