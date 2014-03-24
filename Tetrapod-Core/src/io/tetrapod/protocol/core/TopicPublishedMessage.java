package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.serialize.*;
import io.tetrapod.core.rpc.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class TopicPublishedMessage extends Message {
   
   public static final int STRUCT_ID = 6873263;
    
   public TopicPublishedMessage() {
      defaults();
   }

   public TopicPublishedMessage(int ownerId, int topicId) {
      this.ownerId = ownerId;
      this.topicId = topicId;
   }   
   
   public int ownerId;
   public int topicId;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      ownerId = 0;
      topicId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.ownerId);
      data.write(2, this.topicId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.ownerId = data.read_int(tag); break;
            case 2: this.topicId = data.read_int(tag); break;
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
      return TopicPublishedMessage.STRUCT_ID;
   }
   
   @Override
   public final void dispatch(SubscriptionAPI api, MessageContext ctx) {
      if (api instanceof Handler)
         ((Handler)api).messageTopicPublished(this, ctx);
      else
         api.genericMessage(this, ctx);
   }
   
   public static interface Handler extends SubscriptionAPI {
      void messageTopicPublished(TopicPublishedMessage m, MessageContext ctx);
   }
   
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new TopicPublishedMessage(); }
      };
   }
   
   public final int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }
}
