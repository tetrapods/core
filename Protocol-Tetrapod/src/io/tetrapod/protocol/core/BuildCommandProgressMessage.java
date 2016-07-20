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
 * Sent every 10s or so for display in the UI while it's running
 */

@SuppressWarnings("all")
public class BuildCommandProgressMessage extends Message {
   
   public static final int STRUCT_ID = 1646916;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = TetrapodContract.SUB_CONTRACT_ID;

   public BuildCommandProgressMessage() {
      defaults();
   }

   public BuildCommandProgressMessage(String output, boolean isDone) {
      this.output = output;
      this.isDone = isDone;
   }   
   
   public String output;
   public boolean isDone;

   public final Structure.Security getSecurity() {
      return Security.ADMIN;
   }

   public final void defaults() {
      output = null;
      isDone = false;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.output);
      data.write(2, this.isDone);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.output = data.read_string(tag); break;
            case 2: this.isDone = data.read_boolean(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return BuildCommandProgressMessage.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return BuildCommandProgressMessage.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return BuildCommandProgressMessage.STRUCT_ID;
   }
   
   @Override
   public final void dispatch(SubscriptionAPI api, MessageContext ctx) {
      if (api instanceof Handler)
         ((Handler)api).messageBuildCommandProgress(this, ctx);
      else
         api.genericMessage(this, ctx);
   }
   
   public static interface Handler extends SubscriptionAPI {
      void messageBuildCommandProgress(BuildCommandProgressMessage m, MessageContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "output";
      result[2] = "isDone";
      return result;
   }
   
   public final Structure make() {
      return new BuildCommandProgressMessage();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();     
      desc.name = "BuildCommandProgressMessage";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      return desc;
   }
}
