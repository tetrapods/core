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
public class WebRootAddedMessage extends Message {
   
   public static final int STRUCT_ID = 270402;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
    
   public WebRootAddedMessage() {
      defaults();
   }

   public WebRootAddedMessage(WebRootDef def) {
      this.def = def;
   }   
   
   public WebRootDef def;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      def = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      if (this.def != null) data.write(1, this.def);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.def = data.read_struct(tag, new WebRootDef()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return WebRootAddedMessage.CONTRACT_ID;
   }

   public final int getStructId() {
      return WebRootAddedMessage.STRUCT_ID;
   }
   
   @Override
   public final void dispatch(SubscriptionAPI api, MessageContext ctx) {
      if (api instanceof Handler)
         ((Handler)api).messageWebRootAdded(this, ctx);
      else
         api.genericMessage(this, ctx);
   }
   
   public static interface Handler extends SubscriptionAPI {
      void messageWebRootAdded(WebRootAddedMessage m, MessageContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[1+1];
      result[1] = "def";
      return result;
   }
   
   public final Structure make() {
      return new WebRootAddedMessage();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();     
      desc.name = "WebRootAddedMessage";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRUCT, WebRootDef.CONTRACT_ID, WebRootDef.STRUCT_ID);
      return desc;
   }
}
