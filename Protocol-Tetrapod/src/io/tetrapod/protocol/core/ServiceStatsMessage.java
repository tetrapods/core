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

@SuppressWarnings("unused")
public class ServiceStatsMessage extends Message {
   
   public static final int STRUCT_ID = 469976;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
    
   public ServiceStatsMessage() {
      defaults();
   }

   public ServiceStatsMessage(int entityId, int rps, int mps, long latency, long counter) {
      this.entityId = entityId;
      this.rps = rps;
      this.mps = mps;
      this.latency = latency;
      this.counter = counter;
   }   
   
   public int entityId;
   public int rps;
   public int mps;
   public long latency;
   public long counter;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      entityId = 0;
      rps = 0;
      mps = 0;
      latency = 0;
      counter = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.entityId);
      data.write(2, this.rps);
      data.write(3, this.mps);
      data.write(4, this.latency);
      data.write(5, this.counter);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.entityId = data.read_int(tag); break;
            case 2: this.rps = data.read_int(tag); break;
            case 3: this.mps = data.read_int(tag); break;
            case 4: this.latency = data.read_long(tag); break;
            case 5: this.counter = data.read_long(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return ServiceStatsMessage.CONTRACT_ID;
   }

   public final int getStructId() {
      return ServiceStatsMessage.STRUCT_ID;
   }
   
   @Override
   public final void dispatch(SubscriptionAPI api, MessageContext ctx) {
      if (api instanceof Handler)
         ((Handler)api).messageServiceStats(this, ctx);
      else
         api.genericMessage(this, ctx);
   }
   
   public static interface Handler extends SubscriptionAPI {
      void messageServiceStats(ServiceStatsMessage m, MessageContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[5+1];
      result[1] = "entityId";
      result[2] = "rps";
      result[3] = "mps";
      result[4] = "latency";
      result[5] = "counter";
      return result;
   }
   
   public final Structure make() {
      return new ServiceStatsMessage();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      return desc;
   }
}
