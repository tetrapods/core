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
public class TypeDescriptor extends Structure {
   
   public static final byte T_BOOLEAN = 1; 
   public static final byte T_BYTE = 2; 
   public static final byte T_INT = 3; 
   public static final byte T_LONG = 4; 
   public static final byte T_DOUBLE = 5; 
   public static final byte T_STRING = 6; 
   public static final byte T_STRUCT = 7; 
   public static final byte T_BOOLEAN_LIST = 8; 
   public static final byte T_BYTE_LIST = 9; 
   public static final byte T_INT_LIST = 10; 
   public static final byte T_LONG_LIST = 11; 
   public static final byte T_DOUBLE_LIST = 12; 
   public static final byte T_STRING_LIST = 13; 
   public static final byte T_STRUCT_LIST = 14; 
   
   public static final int STRUCT_ID = 6493266;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
    
   public TypeDescriptor() {
      defaults();
   }

   public TypeDescriptor(byte type, int contractId, int structId) {
      this.type = type;
      this.contractId = contractId;
      this.structId = structId;
   }   
   
   public byte type;
   public int contractId;
   public int structId;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      type = 0;
      contractId = 0;
      structId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.type);
      data.write(2, this.contractId);
      data.write(3, this.structId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.type = data.read_byte(tag); break;
            case 2: this.contractId = data.read_int(tag); break;
            case 3: this.structId = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   public final int getContractId() {
      return TypeDescriptor.CONTRACT_ID;
   }

   public final int getStructId() {
      return TypeDescriptor.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "type";
      result[2] = "contractId";
      result[3] = "structId";
      return result;
   }

   public final Structure make() {
      return new TypeDescriptor();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "TypeDescriptor";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_BYTE, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }

   @Override
   @SuppressWarnings("RedundantIfStatement")
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      TypeDescriptor that = (TypeDescriptor) o;

      if (type != that.type)
         return false;
      if (contractId != that.contractId)
         return false;
      if (structId != that.structId)
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = 0;
      result = 31 * result + type;
      result = 31 * result + contractId;
      result = 31 * result + structId;
      return result;
   }

}
