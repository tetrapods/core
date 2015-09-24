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

@SuppressWarnings("unused")
public class InstallSnapshotRequest extends Request {

   public static final int STRUCT_ID = 5436535;
   public static final int CONTRACT_ID = RaftContract.CONTRACT_ID;
   
   public InstallSnapshotRequest() {
      defaults();
   }

   public InstallSnapshotRequest(long term, long index, long length, int partSize, int part, byte[] data) {
      this.term = term;
      this.index = index;
      this.length = length;
      this.partSize = partSize;
      this.part = part;
      this.data = data;
   }   

   public long term;
   public long index;
   public long length;
   public int partSize;
   public int part;
   public byte[] data;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      term = 0;
      index = 0;
      length = 0;
      partSize = 0;
      part = 0;
      data = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.term);
      data.write(2, this.index);
      data.write(3, this.length);
      data.write(4, this.partSize);
      data.write(5, this.part);
      if (this.data != null) data.write(6, this.data);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.term = data.read_long(tag); break;
            case 2: this.index = data.read_long(tag); break;
            case 3: this.length = data.read_long(tag); break;
            case 4: this.partSize = data.read_int(tag); break;
            case 5: this.part = data.read_int(tag); break;
            case 6: this.data = data.read_byte_array(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return InstallSnapshotRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return InstallSnapshotRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestInstallSnapshot(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestInstallSnapshot(InstallSnapshotRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[6+1];
      result[1] = "term";
      result[2] = "index";
      result[3] = "length";
      result[4] = "partSize";
      result[5] = "part";
      result[6] = "data";
      return result;
   }
   
   public final Structure make() {
      return new InstallSnapshotRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "InstallSnapshotRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[6] = new TypeDescriptor(TypeDescriptor.T_BYTE_LIST, 0, 0);
      return desc;
   }

}
