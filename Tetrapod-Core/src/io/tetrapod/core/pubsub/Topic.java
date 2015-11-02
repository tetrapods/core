package io.tetrapod.core.pubsub;

import static io.tetrapod.protocol.core.TetrapodContract.PARENT_ID_MASK;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import io.tetrapod.core.rpc.Message;
import io.tetrapod.protocol.core.*;

public class Topic {

   public final Publisher                   publisher;
   public final int                         topicId;

   private final Map<Integer, Subscriber>   subscribers = new ConcurrentHashMap<>();
   private final Map<Integer, Subscriber>   tetrapods   = new ConcurrentHashMap<>();
   private final List<SubscriptionListener> listeners   = new ArrayList<>();

   public interface SubscriptionListener {
      public void onTopicSubscribed(int entityId);
   }

   public Topic(Publisher publisher, int topicId) {
      this.publisher = publisher;
      this.topicId = topicId;
   }

   public class Subscriber {
      public final int entityId;

      public Subscriber(int entityId) {
         this.entityId = entityId;
      }
   }

   public void reset() {
      subscribers.clear();
      tetrapods.clear();
   }

   public void addListener(SubscriptionListener listener) {
      synchronized (listeners) {
         listeners.add(listener);
      }
   }

   public void removeListener(SubscriptionListener listener) {
      synchronized (listeners) {
         listeners.remove(listener);
      }
   }

   protected void fireTopicSubscribedEvent(int entityId) {
      synchronized (listeners) {
         for (SubscriptionListener sl : listeners) {
            sl.onTopicSubscribed(entityId);
         }
      }
   }

   // FIXME: Implement once/counting?
   public synchronized void subscribe(int entityId, boolean once) {
      Subscriber sub = subscribers.get(entityId);
      if (sub == null) {
         sub = new Subscriber(entityId);
         subscribers.put(entityId, sub);

         final int parentId = entityId & PARENT_ID_MASK;
         Subscriber tetrapod = tetrapods.get(parentId);
         if (tetrapod == null) {
            tetrapod = new Subscriber(parentId);
            tetrapods.put(parentId, tetrapod);

            // publish the topic on the new tetrapod
            publisher.sendMessage(new TopicPublishedMessage(publisher.getEntityId(), topicId), tetrapod.entityId);
         }
         // register the new subscriber for their parent tetrapod
         publisher.sendMessage(new TopicSubscribedMessage(publisher.getEntityId(), topicId, sub.entityId, once), tetrapod.entityId);
         fireTopicSubscribedEvent(entityId);
      }
   }

   public synchronized void unsubscribe(int entityId) {
      final Subscriber tetrapod = subscribers.get(entityId & PARENT_ID_MASK);
      if (tetrapod != null) {
         publisher.sendMessage(new TopicUnsubscribedMessage(publisher.getEntityId(), topicId, entityId), tetrapod.entityId);
      }
   }

   public synchronized void broadcast(Message msg) {
      // broadcast message to all tetrapods with subscribers to this topic
      for (Subscriber tetrapod : tetrapods.values()) {
         publisher.broadcastMessage(msg, tetrapod.entityId, topicId);
      }
   }

   public synchronized void sendMessage(Message msg, int toEntityId) {
      publisher.sendMessage(msg, toEntityId);
   }

   public synchronized void unpublish() {
      for (Subscriber tetrapod : tetrapods.values()) {
         publisher.sendMessage(new TopicUnpublishedMessage(publisher.getEntityId(), topicId), tetrapod.entityId);
      }
   }

   public synchronized int numSubscribers() {
      return subscribers.size();
   }

   public synchronized Collection<Subscriber> getSubscribers() {
      return subscribers.values();
   }

}
