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
public class WebRoute extends Structure {
   
   public static final int STRUCT_ID = 4890284;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
    
   public WebRoute() {
      defaults();
   }

   public WebRoute(String path, int structId, int contractId) {
      this.path = path;
      this.structId = structId;
      this.contractId = contractId;
   }   
   
   public String path;
   public int structId;
   public int contractId;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      path = null;
      structId = 0;
      contractId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.path);
      data.write(2, this.structId);
      data.write(3, this.contractId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.path = data.read_string(tag); break;
            case 2: this.structId = data.read_int(tag); break;
            case 3: this.contractId = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   public final int getContractId() {
      return WebRoute.CONTRACT_ID;
   }

   public final int getStructId() {
      return WebRoute.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "path";
      result[2] = "structId";
      result[3] = "contractId";
      return result;
   }

   public final Structure make() {
      return new WebRoute();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "WebRoute";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
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

      WebRoute that = (WebRoute) o;

      if (path != null ? !path.equals(that.path) : that.path != null)
         return false;
      if (structId != that.structId)
         return false;
      if (contractId != that.contractId)
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = 0;
      result = 31 * result + (path != null ? path.hashCode() : 0);
      result = 31 * result + structId;
      result = 31 * result + contractId;
      return result;
   }

}
