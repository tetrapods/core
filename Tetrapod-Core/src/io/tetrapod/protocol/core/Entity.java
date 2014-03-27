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
public class Entity extends Structure {
   
   public static final int STRUCT_ID = 10171140;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
    
   public Entity() {
      defaults();
   }

   public Entity(int entityId, int parentId, long reclaimToken, String host, int status, byte type, String name, int build, int version) {
      this.entityId = entityId;
      this.parentId = parentId;
      this.reclaimToken = reclaimToken;
      this.host = host;
      this.status = status;
      this.type = type;
      this.name = name;
      this.build = build;
      this.version = version;
   }   
   
   public int entityId;
   public int parentId;
   public long reclaimToken;
   public String host;
   public int status;
   public byte type;
   public String name;
   public int build;
   public int version;

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
      build = 0;
      version = 0;
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
      data.write(8, this.build);
      data.write(9, this.version);
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
            case 8: this.build = data.read_int(tag); break;
            case 9: this.version = data.read_int(tag); break;
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
      String[] result = new String[9+1];
      result[1] = "entityId";
      result[2] = "parentId";
      result[3] = "reclaimToken";
      result[4] = "host";
      result[5] = "status";
      result[6] = "type";
      result[7] = "name";
      result[8] = "build";
      result[9] = "version";
      return result;
   }

   public final Structure make() {
      return new Entity();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
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
      desc.types[8] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[9] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }
}
