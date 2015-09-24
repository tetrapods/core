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
public class Owner extends Structure {
   
   public static final int STRUCT_ID = 2276990;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
    
   public Owner() {
      defaults();
   }

   public Owner(int entityId, long expiry, List<String> keys, String prefix) {
      this.entityId = entityId;
      this.expiry = expiry;
      this.keys = keys;
      this.prefix = prefix;
   }   
   
   public int entityId;
   public long expiry;
   public List<String> keys;
   public String prefix;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      entityId = 0;
      expiry = 0;
      keys = null;
      prefix = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.entityId);
      data.write(2, this.expiry);
      if (this.keys != null) data.write_string(3, this.keys);
      data.write(4, this.prefix);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.entityId = data.read_int(tag); break;
            case 2: this.expiry = data.read_long(tag); break;
            case 3: this.keys = data.read_string_list(tag); break;
            case 4: this.prefix = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return Owner.CONTRACT_ID;
   }

   public final int getStructId() {
      return Owner.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[4+1];
      result[1] = "entityId";
      result[2] = "expiry";
      result[3] = "keys";
      result[4] = "prefix";
      return result;
   }

   public final Structure make() {
      return new Owner();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "Owner";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING_LIST, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }
}
