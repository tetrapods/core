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
public class Entity extends Structure {
   
   public static final int STRUCT_ID = 10171140;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
    
   public Entity() {
      defaults();
   }

   public Entity(int entityId, int parentId, long reclaimToken, String host, int status, byte type, String name, int version, int contractId, String build) {
      this.entityId = entityId;
      this.parentId = parentId;
      this.reclaimToken = reclaimToken;
      this.host = host;
      this.status = status;
      this.type = type;
      this.name = name;
      this.version = version;
      this.contractId = contractId;
      this.build = build;
   }   
   
   public int entityId;
   public int parentId;
   public long reclaimToken;
   public String host;
   public int status;
   public byte type;
   public String name;
   public int version;
   public int contractId;
   public String build;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      entityId = 0;
      parentId = 0;
      reclaimToken = 0;
      host = null;
      status = 0;
      type = 0;
      name = null;
      version = 0;
      contractId = 0;
      build = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.entityId);
      data.write(2, this.parentId);
      data.write(3, this.reclaimToken);
      data.write(4, this.host);
      data.write(5, this.status);
      data.write(6, this.type);
      data.write(7, this.name);
      data.write(9, this.version);
      data.write(10, this.contractId);
      data.write(11, this.build);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.entityId = data.read_int(tag); break;
            case 2: this.parentId = data.read_int(tag); break;
            case 3: this.reclaimToken = data.read_long(tag); break;
            case 4: this.host = data.read_string(tag); break;
            case 5: this.status = data.read_int(tag); break;
            case 6: this.type = data.read_byte(tag); break;
            case 7: this.name = data.read_string(tag); break;
            case 9: this.version = data.read_int(tag); break;
            case 10: this.contractId = data.read_int(tag); break;
            case 11: this.build = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   public final int getContractId() {
      return Entity.CONTRACT_ID;
   }

   public final int getStructId() {
      return Entity.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[11+1];
      result[1] = "entityId";
      result[2] = "parentId";
      result[3] = "reclaimToken";
      result[4] = "host";
      result[5] = "status";
      result[6] = "type";
      result[7] = "name";
      result[9] = "version";
      result[10] = "contractId";
      result[11] = "build";
      return result;
   }

   public final Structure make() {
      return new Entity();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "Entity";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[6] = new TypeDescriptor(TypeDescriptor.T_BYTE, 0, 0);
      desc.types[7] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[9] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[10] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[11] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

   @Override
   @SuppressWarnings("RedundantIfStatement")
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      Entity that = (Entity) o;

      if (entityId != that.entityId)
         return false;
      if (parentId != that.parentId)
         return false;
      if (reclaimToken != that.reclaimToken)
         return false;
      if (host != null ? !host.equals(that.host) : that.host != null)
         return false;
      if (status != that.status)
         return false;
      if (type != that.type)
         return false;
      if (name != null ? !name.equals(that.name) : that.name != null)
         return false;
      if (version != that.version)
         return false;
      if (contractId != that.contractId)
         return false;
      if (build != null ? !build.equals(that.build) : that.build != null)
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = 0;
      result = 31 * result + entityId;
      result = 31 * result + parentId;
      result = 31 * result + (int) (reclaimToken ^ (reclaimToken >>> 32));
      result = 31 * result + (host != null ? host.hashCode() : 0);
      result = 31 * result + status;
      result = 31 * result + type;
      result = 31 * result + (name != null ? name.hashCode() : 0);
      result = 31 * result + version;
      result = 31 * result + contractId;
      result = 31 * result + (build != null ? build.hashCode() : 0);
      return result;
   }

}
