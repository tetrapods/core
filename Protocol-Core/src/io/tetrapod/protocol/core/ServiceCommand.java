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

/**
 * allows an empty or one-string-arg-called-data request to be called from admin app's service menu
 */

@SuppressWarnings("all")
public class ServiceCommand extends Structure {
   
   public static final int STRUCT_ID = 5461687;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = CoreContract.SUB_CONTRACT_ID;

   public ServiceCommand() {
      defaults();
   }

   public ServiceCommand(String name, String icon, int contractId, int structId, boolean hasArgument) {
      this.name = name;
      this.icon = icon;
      this.contractId = contractId;
      this.structId = structId;
      this.hasArgument = hasArgument;
   }   
   
   public String name;
   public String icon;
   public int contractId;
   public int structId;
   public boolean hasArgument;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      name = null;
      icon = null;
      contractId = 0;
      structId = 0;
      hasArgument = false;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.name);
      data.write(2, this.icon);
      data.write(3, this.contractId);
      data.write(4, this.structId);
      data.write(5, this.hasArgument);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.name = data.read_string(tag); break;
            case 2: this.icon = data.read_string(tag); break;
            case 3: this.contractId = data.read_int(tag); break;
            case 4: this.structId = data.read_int(tag); break;
            case 5: this.hasArgument = data.read_boolean(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   public final int getContractId() {
      return ServiceCommand.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return ServiceCommand.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return ServiceCommand.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[5+1];
      result[1] = "name";
      result[2] = "icon";
      result[3] = "contractId";
      result[4] = "structId";
      result[5] = "hasArgument";
      return result;
   }

   public final Structure make() {
      return new ServiceCommand();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "ServiceCommand";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      return desc;
   }

   @Override
   @SuppressWarnings("RedundantIfStatement")
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      ServiceCommand that = (ServiceCommand) o;

      if (name != null ? !name.equals(that.name) : that.name != null)
         return false;
      if (icon != null ? !icon.equals(that.icon) : that.icon != null)
         return false;
      if (contractId != that.contractId)
         return false;
      if (structId != that.structId)
         return false;
      if (hasArgument != that.hasArgument)
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = 0;
      result = 31 * result + (name != null ? name.hashCode() : 0);
      result = 31 * result + (icon != null ? icon.hashCode() : 0);
      result = 31 * result + contractId;
      result = 31 * result + structId;
      result = 31 * result + (hasArgument ? 1 : 0);
      return result;
   }

}
