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
public class ContractDescription extends Structure {
   
   public static final int STRUCT_ID = 7323457;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
    
   public ContractDescription() {
      defaults();
   }

   public ContractDescription(int contractId, int version, List<StructDescription> structs, WebRoute[] routes) {
      this.contractId = contractId;
      this.version = version;
      this.structs = structs;
      this.routes = routes;
   }   
   
   public int contractId;
   public int version;
   public List<StructDescription> structs;
   public WebRoute[] routes;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      contractId = 0;
      version = 0;
      structs = null;
      routes = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.contractId);
      data.write(2, this.version);
      if (this.structs != null) data.write_struct(3, this.structs);
      if (this.routes != null) data.write(4, this.routes);
      data.writeEndTag();
   }
   
   @SuppressWarnings("Duplicates")
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.contractId = data.read_int(tag); break;
            case 2: this.version = data.read_int(tag); break;
            case 3: this.structs = data.read_struct_list(tag, new StructDescription()); break;
            case 4: this.routes = data.read_struct_array(tag, new WebRoute()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   public final int getContractId() {
      return ContractDescription.CONTRACT_ID;
   }

   public final int getStructId() {
      return ContractDescription.STRUCT_ID;
   }

   @SuppressWarnings("Duplicates")
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[4+1];
      result[1] = "contractId";
      result[2] = "version";
      result[3] = "structs";
      result[4] = "routes";
      return result;
   }

   public final Structure make() {
      return new ContractDescription();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "ContractDescription";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, StructDescription.CONTRACT_ID, StructDescription.STRUCT_ID);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, WebRoute.CONTRACT_ID, WebRoute.STRUCT_ID);
      return desc;
   }

   @Override
   @SuppressWarnings("RedundantIfStatement")
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      ContractDescription that = (ContractDescription) o;

      if (contractId != that.contractId)
         return false;
      if (version != that.version)
         return false;
      if (structs != null ? !structs.equals(that.structs) : that.structs != null)
         return false;
      if (!Arrays.equals(routes, that.routes))
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = 0;
      result = 31 * result + contractId;
      result = 31 * result + version;
      result = 31 * result + (structs != null ? structs.hashCode() : 0);
      result = 31 * result + Arrays.hashCode(routes);
      return result;
   }

}
