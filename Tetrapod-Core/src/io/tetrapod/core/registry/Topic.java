package io.tetrapod.core.registry;

import java.util.*;

/**
 * A data structure to manage a Topic for pubsub. Topics have an owner, a reference ID, and a list of subscribers. If the same client
 * subscribes to the topic multiple times, we increment a reference counter. A subscription is fully unsubscribed if the counter drops to
 * zero.
 */
public class Topic {
   public final int                       topicId;
   public final int                       ownerId;

   private final Map<Integer, Subscriber> subscribers = new HashMap<>();

   public Topic(int topicId, int ownerId) {
      this.topicId = topicId;
      this.ownerId = ownerId;
   }

   /**
    * Add a client as a subscriber.
    * 
    * @return true if the client was not already subscribed
    */
   public synchronized boolean subscribe(int id) {
      Subscriber sub = subscribers.get(id);
      if (sub == null) {
         sub = new Subscriber(id);
         subscribers.put(id, sub);
      }
      sub.counter++;
      return sub.counter == 1;
   }

   /**
    * Unsubscribe a client from this topic. We decrement their counter, and only unsubscribe if it drops to zero.
    * 
    * @return true if we fully unsubscribed the client
    */
   public synchronized boolean unsubscribe(int id) {
      Subscriber sub = subscribers.get(id);
      if (sub != null) {
         sub.counter--;
         if (sub.counter == 0) {
            subscribers.remove(id);
            return true;
         }
      }
      return false;
   }

   @Override
   public String toString() {
      return String.format("Topic-%d-%d [%s]", ownerId, topicId);
   }

   /**
    * Get the total number of unique subscribers to this topic
    */
   public int getNumScubscribers() {
      return subscribers.size();
   }
}
