package io.tetrapod.core.pubsub;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.tetrapod.core.rpc.Message;
import io.tetrapod.protocol.core.*;

public class Topic {
   private static final Logger                  logger      = LoggerFactory.getLogger(Topic.class);

   public final Publisher                       publisher;
   public final int                             topicId;

   private final Map<Long, Subscriber>          subscribers = new ConcurrentHashMap<>();
   private final Map<Integer, ParentSubscriber> parents     = new ConcurrentHashMap<>();
   private final List<SubscriptionListener>     listeners   = new ArrayList<>();

   public interface SubscriptionListener {
      public void onTopicSubscribed(int entityId, int childId, boolean resub);
   }

   public Topic(Publisher publisher, int topicId) {
      this.publisher = publisher;
      this.topicId = topicId;
   }

   @Override
   public String toString() {
      return String.format("Topic-%d", topicId);
   }
   
   public static long makeKey(int entityId, int childId) {
      return ((long) entityId << 32) | childId;
   }

   public static class Subscriber {
      public final int entityId;
      public final int childId;

      public Subscriber(int entityId, int childId) {
         this.entityId = entityId;
         this.childId = childId;
      }

      public long key() {
         return makeKey(entityId, childId);
      }
   }

   public static class ParentSubscriber extends Subscriber {
      public boolean init = false;

      public ParentSubscriber(int entityId) {
         super(entityId, 0);
      }
   }

   public void reset() {
      subscribers.clear();
      parents.clear();
   }

   public void reset(int parentId) {
      ParentSubscriber parent = parents.get(parentId);
      if (parent != null) {
         parent.init = false;
      }
   }

   /**
    * Delete a parent & all child subs
    */
   public void clear(int parentId) {
      ParentSubscriber parent = parents.remove(parentId);
      if (parent != null) {
         logger.info("Clearing topic {} subscriptions for parent-{}", this, parentId);
         for (Subscriber s : subscribers.values()) {
            if (s.entityId == parentId) {
               logger.info("Clearing topic {} subscriptions for child-{}", this, s.childId);
               subscribers.remove(s.key());
            }
         }
      }
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

   protected void fireTopicSubscribedEvent(int entityId, int childId, boolean resub) {
      synchronized (listeners) {
         for (SubscriptionListener sl : listeners) {
            sl.onTopicSubscribed(entityId, childId, resub);
         }
      }
   }

   public synchronized void subscribe(int entityId, int childId, boolean once) {
      Subscriber sub = subscribers.get(makeKey(entityId, childId));
      if (sub == null) {
         sub = new Subscriber(entityId, childId);
         subscribers.put(sub.key(), sub);
      }
      final int parentId = entityId;
      ParentSubscriber parent = parents.get(parentId);
      if (parent == null) {
         parent = new ParentSubscriber(parentId);
         parents.put(parentId, parent);

         // publish the topic on the parent
         if (publisher.sendMessage(new TopicPublishedMessage(publisher.getEntityId(), topicId), parent.entityId, 0, topicId)) {
            parent.init = true;
         }
      }

      // register the new subscriber for their parent 
      publisher.sendMessage(new TopicSubscribedMessage(publisher.getEntityId(), topicId, sub.entityId, childId, once), parent.entityId, 0,
            topicId);
      fireTopicSubscribedEvent(entityId, childId, false);

   }

   public synchronized void unsubscribe(int entityId, int childId) {
      logger.info("{} unsubscribe {} {}", this, entityId, childId);
      Subscriber sub = subscribers.remove(makeKey(entityId, childId));
      if (sub != null) {
         final Subscriber parent = parents.get(entityId);
         if (parent != null) {
            publisher.sendMessage(new TopicUnsubscribedMessage(publisher.getEntityId(), topicId, entityId, childId), parent.entityId, 0,
                  topicId);
         }
      }
   }

   public synchronized void broadcast(Message msg) {
      // broadcast message to all parents with subscribers to this topic
      for (ParentSubscriber parent : parents.values()) {
         if (!parent.init) {
            if (publisher.sendMessage(new TopicPublishedMessage(publisher.getEntityId(), topicId), parent.entityId, 0, topicId)) {
               logger.info("HAVE TO REPUBLISH TOPIC {} to Parent-{}", topicId, parent.entityId);
               for (Subscriber sub : subscribers.values()) {
                  final int parentId = sub.entityId;
                  if (parentId == parent.entityId) {
                     publisher.sendMessage(new TopicSubscribedMessage(publisher.getEntityId(), topicId, sub.entityId, sub.childId, true),
                           parent.entityId, 0, topicId);
                     fireTopicSubscribedEvent(sub.entityId, sub.childId, true);
                  }
               }
               parent.init = true;
            } else {
               parent.init = false;
            }
         }
         if (!publisher.broadcastMessage(msg, parent.entityId, topicId)) {
            parent.init = false;
         }
      }
   }

   public synchronized void sendMessage(Message msg, int toEntityId, int childId) {
      publisher.sendMessage(msg, toEntityId, childId, topicId);
   }

   public synchronized void unpublish() {
      publisher.unpublish(topicId);
   }

   // Called by Publisher.unpublish()
   protected synchronized void unpublishAll() {
      for (Subscriber parent : parents.values()) {
         publisher.sendMessage(new TopicUnpublishedMessage(publisher.getEntityId(), topicId), parent.entityId, 0, topicId);
      }
   }

   public synchronized int numSubscribers() {
      return subscribers.size();
   }

   public synchronized Collection<Subscriber> getSubscribers() {
      return subscribers.values();
   }

}
