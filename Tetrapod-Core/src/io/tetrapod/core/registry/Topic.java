package io.tetrapod.core.registry;

import io.tetrapod.protocol.core.Subscriber;

import java.util.*;

/**
 * A data structure to manage a Topic for pub/sub. Topics have an owner, a reference ID, and a list of subscribers. If the same client
 * subscribes to the topic multiple times, we increment a reference counter. A subscription is fully unsubscribed if the counter drops to
 * zero.
 */
public class Topic {
   public final int                       topicId;
   public final int                       ownerId;

   private final Map<Integer, Subscriber> subscribers = new HashMap<>();
   private final Map<Integer, Subscriber> parents     = new HashMap<>();
   private final Map<Integer, Subscriber> children    = new HashMap<>();

   public Topic(int ownerId, int topicId) {
      this.topicId = topicId;
      this.ownerId = ownerId;
   }

   /**
    * Add a client as a subscriber.
    * 
    * @return true if the client was not already subscribed
    */
   public synchronized boolean subscribe(final EntityInfo publisher, final EntityInfo e, final int parentId) {
      Subscriber sub = subscribers.get(e.entityId);
      if (sub == null) {
         sub = new Subscriber(e.entityId, 0);
         subscribers.put(e.entityId, sub);

         if (e.isTetrapod()) {
            if (publisher.parentId == parentId) {
               // if they are a tetrapod and this topic is owned by us, we can deliver directly
               children.put(e.entityId, sub);
            }
         } else { 
            if (e.parentId != parentId) {
               // if we aren't their parent, add them to a proxy subscription 
               Subscriber psub = parents.get(parentId);
               if (psub == null) {
                  psub = new Subscriber(parentId, 0);
                  parents.put(parentId, psub);
               }
               psub.counter++;
            } else {
               // we are their parent, so we can deliver messages directly
               children.put(e.entityId, sub);
            }
         }
      }
      sub.counter++;

      return sub.counter == 1;
   }

   /**
    * Unsubscribe a client from this topic. We decrement their counter, and only unsubscribe if it drops to zero.
    * 
    * @return true if we fully unsubscribed the client
    */
   public synchronized boolean unsubscribe(int entityId, int parentId, boolean proxy, boolean all) {
      final Subscriber sub = subscribers.get(entityId);
      if (sub != null) {
         sub.counter--;
         if (sub.counter == 0 || all) {
            subscribers.remove(entityId);
            children.remove(entityId);
            if (proxy) {
               Subscriber psub = parents.get(parentId);
               psub.counter--;
               if (psub.counter == 0) {
                  parents.remove(parentId);
               }
            }
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
   public synchronized int getNumScubscribers() {
      return subscribers.size();
   }

   public synchronized Collection<Subscriber> getChildSubscribers() {
      return children.values();
   }

   public synchronized Collection<Subscriber> getProxySubscribers() {
      return parents.values();
   }

   public synchronized Collection<Subscriber> getSubscribers() {
      return subscribers.values();
   }

   public long key() {
      return ((long) (ownerId) << 32) | (long) topicId;
   }
}
