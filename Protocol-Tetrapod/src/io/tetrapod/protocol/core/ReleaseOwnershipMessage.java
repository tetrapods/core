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

@SuppressWarnings("all")
public class ReleaseOwnershipMessage extends Message {
   
   public static final int STRUCT_ID = 9542348;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
    
   public ReleaseOwnershipMessage() {
      defaults();
   }

   public ReleaseOwnershipMessage(int entityId, String prefix, String[] keys) {
      this.entityId = entityId;
      this.prefix = prefix;
      this.keys = keys;
   }   
   
   public int entityId;
   public String prefix;
   public String[] keys;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      entityId = 0;
      prefix = null;
      keys = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.entityId);
      data.write(2, this.prefix);
      if (this.keys != null) data.write(3, this.keys);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.entityId = data.read_int(tag); break;
            case 2: this.prefix = data.read_string(tag); break;
            case 3: this.keys = data.read_string_array(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return ReleaseOwnershipMessage.CONTRACT_ID;
   }

   public final int getStructId() {
      return ReleaseOwnershipMessage.STRUCT_ID;
   }
   
   @Override
   public final void dispatch(SubscriptionAPI api, MessageContext ctx) {
      if (api instanceof Handler)
         ((Handler)api).messageReleaseOwnership(this, ctx);
      else
         api.genericMessage(this, ctx);
   }
   
   public static interface Handler extends SubscriptionAPI {
      void messageReleaseOwnership(ReleaseOwnershipMessage m, MessageContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "entityId";
      result[2] = "prefix";
      result[3] = "keys";
      return result;
   }
   
   public final Structure make() {
      return new ReleaseOwnershipMessage();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();     
      desc.name = "ReleaseOwnershipMessage";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING_LIST, 0, 0);
      return desc;
   }
}
