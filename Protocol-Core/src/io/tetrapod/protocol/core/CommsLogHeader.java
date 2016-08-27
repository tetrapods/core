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
public class CommsLogHeader extends Structure {
   
   public static final int STRUCT_ID = 8830315;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = CoreContract.SUB_CONTRACT_ID;

   public CommsLogHeader() {
      defaults();
   }

   public CommsLogHeader(long timestamp, LogHeaderType type) {
      this.timestamp = timestamp;
      this.type = type;
   }   
   
   public long timestamp;
   public LogHeaderType type;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      timestamp = 0;
      type = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.timestamp);
      if (this.type != null) data.write(2, this.type.value);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.timestamp = data.read_long(tag); break;
            case 2: this.type = LogHeaderType.from(data.read_int(tag)); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   public final int getContractId() {
      return CommsLogHeader.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return CommsLogHeader.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return CommsLogHeader.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "timestamp";
      result[2] = "type";
      return result;
   }

   public final Structure make() {
      return new CommsLogHeader();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "CommsLogHeader";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }

   @Override
   @SuppressWarnings("RedundantIfStatement")
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      CommsLogHeader that = (CommsLogHeader) o;

      if (timestamp != that.timestamp)
         return false;
      if (type != null ? !type.equals(that.type) : that.type != null)
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = 0;
      result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
      result = 31 * result + (type != null ? type.hashCode() : 0);
      return result;
   }

}
