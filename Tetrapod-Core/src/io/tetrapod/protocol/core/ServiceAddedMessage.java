package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.serialize.*;
import io.tetrapod.core.rpc.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class ServiceAddedMessage extends Message {
   
   public static final int STRUCT_ID = 15116807;
    
   public ServiceAddedMessage() {
      defaults();
   }

   public ServiceAddedMessage(Entity entity) {
      this.entity = entity;
   }   
   
   public Entity entity;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      entity = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      if (this.entity != null) data.write(1, this.entity);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.entity = data.read_struct(tag, Entity.class); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   @Override
   public final int getStructId() {
      return ServiceAddedMessage.STRUCT_ID;
   }
   
   @Override
   public final void dispatch(SubscriptionAPI api, MessageContext ctx) {
      if (api instanceof Handler)
         ((Handler)api).messageServiceAdded(this, ctx);
      else
         api.genericMessage(this, ctx);
   }
   
   public static interface Handler extends SubscriptionAPI {
      void messageServiceAdded(ServiceAddedMessage m, MessageContext ctx);
   }
   
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new ServiceAddedMessage(); }
      };
   }
   
   public final int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }
}
