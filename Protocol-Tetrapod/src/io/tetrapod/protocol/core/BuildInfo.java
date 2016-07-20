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
public class BuildInfo extends Structure {
   
   public static final int STRUCT_ID = 14488001;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = TetrapodContract.SUB_CONTRACT_ID;

   public BuildInfo() {
      defaults();
   }

   public BuildInfo(String serviceName, boolean canBuild, boolean canDeploy, boolean canLaunch, int currentBuild, int[] knownBuilds) {
      this.serviceName = serviceName;
      this.canBuild = canBuild;
      this.canDeploy = canDeploy;
      this.canLaunch = canLaunch;
      this.currentBuild = currentBuild;
      this.knownBuilds = knownBuilds;
   }   
   
   public String serviceName;
   public boolean canBuild;
   public boolean canDeploy;
   public boolean canLaunch;
   public int currentBuild;
   public int[] knownBuilds;

   public final Structure.Security getSecurity() {
      return Security.ADMIN;
   }

   public final void defaults() {
      serviceName = null;
      canBuild = false;
      canDeploy = false;
      canLaunch = false;
      currentBuild = 0;
      knownBuilds = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.serviceName);
      data.write(2, this.canBuild);
      data.write(3, this.canDeploy);
      data.write(4, this.canLaunch);
      data.write(5, this.currentBuild);
      if (this.knownBuilds != null) data.write(6, this.knownBuilds);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.serviceName = data.read_string(tag); break;
            case 2: this.canBuild = data.read_boolean(tag); break;
            case 3: this.canDeploy = data.read_boolean(tag); break;
            case 4: this.canLaunch = data.read_boolean(tag); break;
            case 5: this.currentBuild = data.read_int(tag); break;
            case 6: this.knownBuilds = data.read_int_array(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   public final int getContractId() {
      return BuildInfo.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return BuildInfo.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return BuildInfo.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[6+1];
      result[1] = "serviceName";
      result[2] = "canBuild";
      result[3] = "canDeploy";
      result[4] = "canLaunch";
      result[5] = "currentBuild";
      result[6] = "knownBuilds";
      return result;
   }

   public final Structure make() {
      return new BuildInfo();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "BuildInfo";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[6] = new TypeDescriptor(TypeDescriptor.T_INT_LIST, 0, 0);
      return desc;
   }

   @Override
   @SuppressWarnings("RedundantIfStatement")
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      BuildInfo that = (BuildInfo) o;

      if (serviceName != null ? !serviceName.equals(that.serviceName) : that.serviceName != null)
         return false;
      if (canBuild != that.canBuild)
         return false;
      if (canDeploy != that.canDeploy)
         return false;
      if (canLaunch != that.canLaunch)
         return false;
      if (currentBuild != that.currentBuild)
         return false;
      if (!Arrays.equals(knownBuilds, that.knownBuilds))
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = 0;
      result = 31 * result + (serviceName != null ? serviceName.hashCode() : 0);
      result = 31 * result + (canBuild ? 1 : 0);
      result = 31 * result + (canDeploy ? 1 : 0);
      result = 31 * result + (canLaunch ? 1 : 0);
      result = 31 * result + currentBuild;
      result = 31 * result + Arrays.hashCode(knownBuilds);
      return result;
   }

}
