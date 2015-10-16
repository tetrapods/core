package io.tetrapod.core.registry;

import io.tetrapod.protocol.core.Subscriber;

import java.util.*;

/**
 * A data structure to manage a Topic for pub/sub. Topics have an owner, a reference ID, and a list of subscribers. If the same client
 * subscribes to the topic multiple times, we increment a reference counter. A subscription is fully unsubscribed if the counter drops to
 * zero.
 */
public class RegistryTopic {
   public final int                       topicId;
   public final int                       ownerId;

   private final Map<Integer, Subscriber> subscribers = new HashMap<>();

   public RegistryTopic(int ownerId, int topicId) {
      this.topicId = topicId;
      this.ownerId = ownerId;
   }

   /**
    * Add a client as a subscriber.
    * 
    * @return true if the client was not already subscribed
    */
   public synchronized boolean subscribe(final EntityInfo publisher, final EntityInfo e, final boolean once) {
      Subscriber sub = subscribers.get(e.entityId);
      if (sub == null) {
         sub = new Subscriber(e.entityId, 1);
         subscribers.put(e.entityId, sub);
      } else if (!once) {
         sub.counter++;
      }

      return sub.counter == 1;
   }

   /**
    * Unsubscribe a client from this topic. We decrement their counter, and only unsubscribe if it drops to zero.
    * 
    * @return true if we fully unsubscribed the client
    */
   public synchronized boolean unsubscribe(int entityId, boolean all) {
      final Subscriber sub = subscribers.get(entityId);
      if (sub != null) {
         sub.counter--;
         if (sub.counter == 0 || all) {
            subscribers.remove(entityId);
            return true;
         }
      }
      return false;
   }

   @Override
   public String toString() {
      return String.format("Topic-%d-%d", ownerId, topicId);
   }

   /**
    * Get the total number of unique subscribers to this topic
    */
   public synchronized int getNumSubscribers() {
      return subscribers.size();
   }

   public synchronized Collection<Subscriber> getSubscribers() {
      return subscribers.values();
   }

   public long key() {
      return ((long) (ownerId) << 32) | (long) topicId;
   }
}
