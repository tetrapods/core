package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.serialize.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.TypeDescriptor;
import io.tetrapod.protocol.core.StructDescription;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * notification of an existing tetrapod cluster member
 */

@SuppressWarnings("all")
public class ClusterMemberMessage extends Message {
   
   public static final int STRUCT_ID = 1076508;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = TetrapodContract.SUB_CONTRACT_ID;

   public ClusterMemberMessage() {
      defaults();
   }

   public ClusterMemberMessage(int entityId, String host, int servicePort, int clusterPort, String uuid) {
      this.entityId = entityId;
      this.host = host;
      this.servicePort = servicePort;
      this.clusterPort = clusterPort;
      this.uuid = uuid;
   }   
   
   public int entityId;
   public String host;
   public int servicePort;
   public int clusterPort;
   public String uuid;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      entityId = 0;
      host = null;
      servicePort = 0;
      clusterPort = 0;
      uuid = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.entityId);
      data.write(2, this.host);
      data.write(3, this.servicePort);
      data.write(4, this.clusterPort);
      data.write(5, this.uuid);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.entityId = data.read_int(tag); break;
            case 2: this.host = data.read_string(tag); break;
            case 3: this.servicePort = data.read_int(tag); break;
            case 4: this.clusterPort = data.read_int(tag); break;
            case 5: this.uuid = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return ClusterMemberMessage.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return ClusterMemberMessage.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return ClusterMemberMessage.STRUCT_ID;
   }
   
   @Override
   public final void dispatch(SubscriptionAPI api, MessageContext ctx) {
      if (api instanceof Handler)
         ((Handler)api).messageClusterMember(this, ctx);
      else
         api.genericMessage(this, ctx);
   }
   
   public static interface Handler extends SubscriptionAPI {
      void messageClusterMember(ClusterMemberMessage m, MessageContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[5+1];
      result[1] = "entityId";
      result[2] = "host";
      result[3] = "servicePort";
      result[4] = "clusterPort";
      result[5] = "uuid";
      return result;
   }
   
   public final Structure make() {
      return new ClusterMemberMessage();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();     
      desc.name = "ClusterMemberMessage";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }
}
