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
public class RequestHeader extends Structure {
   
   public static final int STRUCT_ID = 7165109;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
    
   public RequestHeader() {
      defaults();
   }

   public RequestHeader(int requestId, int fromParentId, int fromChildId, int toId, byte fromType, byte timeout, int version, int contractId, int structId) {
      this.requestId = requestId;
      this.fromParentId = fromParentId;
      this.fromChildId = fromChildId;
      this.toId = toId;
      this.fromType = fromType;
      this.timeout = timeout;
      this.version = version;
      this.contractId = contractId;
      this.structId = structId;
   }   
   
   public int requestId;
   public int fromParentId;
   public int fromChildId;
   public int toId;
   public byte fromType;
   public byte timeout;
   public int version;
   public int contractId;
   public int structId;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      requestId = 0;
      fromParentId = 0;
      fromChildId = 0;
      toId = 0;
      fromType = 0;
      timeout = 0;
      version = 0;
      contractId = 0;
      structId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.requestId);
      data.write(2, this.fromParentId);
      data.write(3, this.fromChildId);
      data.write(4, this.toId);
      data.write(5, this.fromType);
      data.write(6, this.timeout);
      data.write(7, this.version);
      data.write(8, this.contractId);
      data.write(9, this.structId);
      data.writeEndTag();
   }
   
   @SuppressWarnings("Duplicates")
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.requestId = data.read_int(tag); break;
            case 2: this.fromParentId = data.read_int(tag); break;
            case 3: this.fromChildId = data.read_int(tag); break;
            case 4: this.toId = data.read_int(tag); break;
            case 5: this.fromType = data.read_byte(tag); break;
            case 6: this.timeout = data.read_byte(tag); break;
            case 7: this.version = data.read_int(tag); break;
            case 8: this.contractId = data.read_int(tag); break;
            case 9: this.structId = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   public final int getContractId() {
      return RequestHeader.CONTRACT_ID;
   }

   public final int getStructId() {
      return RequestHeader.STRUCT_ID;
   }

   @SuppressWarnings("Duplicates")
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[9+1];
      result[1] = "requestId";
      result[2] = "fromParentId";
      result[3] = "fromChildId";
      result[4] = "toId";
      result[5] = "fromType";
      result[6] = "timeout";
      result[7] = "version";
      result[8] = "contractId";
      result[9] = "structId";
      return result;
   }

   public final Structure make() {
      return new RequestHeader();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "RequestHeader";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_BYTE, 0, 0);
      desc.types[6] = new TypeDescriptor(TypeDescriptor.T_BYTE, 0, 0);
      desc.types[7] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[8] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[9] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }

   @Override
   @SuppressWarnings("RedundantIfStatement")
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      RequestHeader that = (RequestHeader) o;

      if (requestId != that.requestId)
         return false;
      if (fromId != that.fromId)
         return false;
      if (toId != that.toId)
         return false;
      if (fromType != that.fromType)
         return false;
      if (timeout != that.timeout)
         return false;
      if (version != that.version)
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
      result = 31 * result + requestId;
      result = 31 * result + fromId;
      result = 31 * result + toId;
      result = 31 * result + fromType;
      result = 31 * result + timeout;
      result = 31 * result + version;
      result = 31 * result + contractId;
      result = 31 * result + structId;
      return result;
   }

}
