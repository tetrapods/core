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
public class CommsLogFileHeader extends Structure {
   
   public static final int STRUCT_ID = 11154645;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = CoreContract.SUB_CONTRACT_ID;

   public CommsLogFileHeader() {
      defaults();
   }

   public CommsLogFileHeader(List<StructDescription> structs, String serviceName, int entityId, String build, String host) {
      this.structs = structs;
      this.serviceName = serviceName;
      this.entityId = entityId;
      this.build = build;
      this.host = host;
   }   
   
   public List<StructDescription> structs;
   public String serviceName;
   public int entityId;
   public String build;
   public String host;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      structs = null;
      serviceName = null;
      entityId = 0;
      build = null;
      host = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      if (this.structs != null) data.write_struct(1, this.structs);
      data.write(2, this.serviceName);
      data.write(3, this.entityId);
      data.write(4, this.build);
      data.write(5, this.host);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.structs = data.read_struct_list(tag, new StructDescription()); break;
            case 2: this.serviceName = data.read_string(tag); break;
            case 3: this.entityId = data.read_int(tag); break;
            case 4: this.build = data.read_string(tag); break;
            case 5: this.host = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   public final int getContractId() {
      return CommsLogFileHeader.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return CommsLogFileHeader.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return CommsLogFileHeader.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[5+1];
      result[1] = "structs";
      result[2] = "serviceName";
      result[3] = "entityId";
      result[4] = "build";
      result[5] = "host";
      return result;
   }

   public final Structure make() {
      return new CommsLogFileHeader();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "CommsLogFileHeader";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, StructDescription.CONTRACT_ID, StructDescription.STRUCT_ID);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

   @Override
   @SuppressWarnings("RedundantIfStatement")
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      CommsLogFileHeader that = (CommsLogFileHeader) o;

      if (structs != null ? !structs.equals(that.structs) : that.structs != null)
         return false;
      if (serviceName != null ? !serviceName.equals(that.serviceName) : that.serviceName != null)
         return false;
      if (entityId != that.entityId)
         return false;
      if (build != null ? !build.equals(that.build) : that.build != null)
         return false;
      if (host != null ? !host.equals(that.host) : that.host != null)
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = 0;
      result = 31 * result + (structs != null ? structs.hashCode() : 0);
      result = 31 * result + (serviceName != null ? serviceName.hashCode() : 0);
      result = 31 * result + entityId;
      result = 31 * result + (build != null ? build.hashCode() : 0);
      result = 31 * result + (host != null ? host.hashCode() : 0);
      return result;
   }

}
