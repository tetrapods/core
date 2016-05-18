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
public class ClusterProperty extends Structure {
   
   public static final int STRUCT_ID = 16245306;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
    
   public ClusterProperty() {
      defaults();
   }

   public ClusterProperty(String key, boolean secret, String val) {
      this.key = key;
      this.secret = secret;
      this.val = val;
   }   
   
   public String key;
   public boolean secret;
   public String val;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      key = null;
      secret = false;
      val = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.key);
      data.write(2, this.secret);
      data.write(3, this.val);
      data.writeEndTag();
   }
   
   @SuppressWarnings("Duplicates")
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.key = data.read_string(tag); break;
            case 2: this.secret = data.read_boolean(tag); break;
            case 3: this.val = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   public final int getContractId() {
      return ClusterProperty.CONTRACT_ID;
   }

   public final int getStructId() {
      return ClusterProperty.STRUCT_ID;
   }

   @SuppressWarnings("Duplicates")
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "key";
      result[2] = "secret";
      result[3] = "val";
      return result;
   }

   public final Structure make() {
      return new ClusterProperty();
   }

   protected boolean isSensitive(String fieldName) {
      if (fieldName.equals("val")) return true;
      return false;
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "ClusterProperty";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

   @Override
   @SuppressWarnings("RedundantIfStatement")
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      ClusterProperty that = (ClusterProperty) o;

      if (key != null ? !key.equals(that.key) : that.key != null)
         return false;
      if (secret != that.secret)
         return false;
      if (val != null ? !val.equals(that.val) : that.val != null)
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = 0;
      result = 31 * result + (key != null ? key.hashCode() : 0);
      result = 31 * result + (secret ? 1 : 0);
      result = 31 * result + (val != null ? val.hashCode() : 0);
      return result;
   }

}
