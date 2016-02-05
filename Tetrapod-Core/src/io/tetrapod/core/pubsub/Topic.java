package io.tetrapod.core.pubsub;

import static io.tetrapod.protocol.core.TetrapodContract.PARENT_ID_MASK;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.tetrapod.core.rpc.Message;
import io.tetrapod.protocol.core.*;

public class Topic {
   private static final Logger                    logger      = LoggerFactory.getLogger(Topic.class);

   public final Publisher                         publisher;
   public final int                               topicId;

   private final Map<Integer, Subscriber>         subscribers = new ConcurrentHashMap<>();
   private final Map<Integer, TetrapodSubscriber> tetrapods   = new ConcurrentHashMap<>();
   private final List<SubscriptionListener>       listeners   = new ArrayList<>();

   public interface SubscriptionListener {
      public void onTopicSubscribed(int entityId, boolean resub);
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

   public class TetrapodSubscriber extends Subscriber {
      public boolean init = false;

      public TetrapodSubscriber(int entityId) {
         super(entityId);
      }
   }

   public void reset() {
      subscribers.clear();
      tetrapods.clear();
   }

   public void reset(int tetrapodId) {
      TetrapodSubscriber tetrapod = tetrapods.get(tetrapodId);
      if (tetrapod != null) {
         tetrapod.init = false;
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

   protected void fireTopicSubscribedEvent(int entityId, boolean resub) {
      synchronized (listeners) {
         for (SubscriptionListener sl : listeners) {
            sl.onTopicSubscribed(entityId, resub);
         }
      }
   }

   public synchronized void subscribe(int entityId, boolean once) {
      Subscriber sub = subscribers.get(entityId);
      if (sub == null) {
         sub = new Subscriber(entityId);
         subscribers.put(entityId, sub);

         final int parentId = entityId & PARENT_ID_MASK;
         TetrapodSubscriber tetrapod = tetrapods.get(parentId);
         if (tetrapod == null) {
            tetrapod = new TetrapodSubscriber(parentId);
            tetrapods.put(parentId, tetrapod);

            // publish the topic on the new tetrapod
            if (publisher.sendMessage(new TopicPublishedMessage(publisher.getEntityId(), topicId), tetrapod.entityId, topicId)) {
               tetrapod.init = true;
            }
         }
         // register the new subscriber for their parent tetrapod
         publisher.sendMessage(new TopicSubscribedMessage(publisher.getEntityId(), topicId, sub.entityId, once), tetrapod.entityId, topicId);
         fireTopicSubscribedEvent(entityId, false);
      }
   }

   public synchronized void unsubscribe(int entityId) {
      Subscriber sub = subscribers.remove(entityId);
      if (sub != null) {
         final Subscriber tetrapod = tetrapods.get(entityId & PARENT_ID_MASK);
         if (tetrapod != null) {
            publisher.sendMessage(new TopicUnsubscribedMessage(publisher.getEntityId(), topicId, entityId), tetrapod.entityId, topicId);
         }
      }
   }

   public synchronized void broadcast(Message msg) {
      if (numSubscribers() > 0) {
         // broadcast message to all tetrapods with subscribers to this topic
         for (TetrapodSubscriber tetrapod : tetrapods.values()) {
            if (!tetrapod.init) {
               if (publisher.sendMessage(new TopicPublishedMessage(publisher.getEntityId(), topicId), tetrapod.entityId, topicId)) {
                  logger.info("HAVE TO REPUBLISH TOPIC {} to Tetrapod-{}", topicId, tetrapod.entityId);
                  for (Subscriber sub : subscribers.values()) {
                     final int parentId = sub.entityId & PARENT_ID_MASK;
                     if (parentId == tetrapod.entityId) {
                        publisher.sendMessage(new TopicSubscribedMessage(publisher.getEntityId(), topicId, sub.entityId, true),
                                 tetrapod.entityId, topicId);
                        fireTopicSubscribedEvent(sub.entityId, true);
                     }
                  }
                  tetrapod.init = true;
               } else {
                  tetrapod.init = false;
               }
            }
            if (!publisher.broadcastMessage(msg, tetrapod.entityId, topicId)) {
               tetrapod.init = false;
            }
         }
      }
   }

   public synchronized void sendMessage(Message msg, int toEntityId) {
      publisher.sendMessage(msg, toEntityId, topicId);
   }

   public synchronized void unpublish() {
      publisher.unpublish(topicId);
   }

   // Called by Publisher.unpublish()
   protected synchronized void unpublishAll() {
      for (Subscriber tetrapod : tetrapods.values()) {
         publisher.sendMessage(new TopicUnpublishedMessage(publisher.getEntityId(), topicId), tetrapod.entityId, topicId);
      }
   }

   public synchronized int numSubscribers() {
      return subscribers.size();
   }

   public synchronized Collection<Subscriber> getSubscribers() {
      return subscribers.values();
   }

}
