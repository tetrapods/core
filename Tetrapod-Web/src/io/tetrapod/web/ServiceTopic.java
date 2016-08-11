package io.tetrapod.web;

import java.util.*;

import io.tetrapod.core.utils.SequentialWorkQueue;
import io.tetrapod.protocol.core.Subscriber;

/**
 * A data structure to manage a Topic for pub/sub. Topics have an owner, a reference ID, and a list of subscribers. If the same client
 * subscribes to the topic multiple times, we increment a reference counter. A subscription is fully unsubscribed if the counter drops to
 * zero.
 */
public class ServiceTopic {

   public final int                       topicId;
   public final int                       ownerId;

   private final Map<Integer, Subscriber> subscribers = new HashMap<>();

   protected SequentialWorkQueue          queue       = new SequentialWorkQueue();

   public ServiceTopic(int ownerId, int topicId) {
      this.topicId = topicId;
      this.ownerId = ownerId;
   }

   /**
    * Add a client as a subscriber.
    * 
    * @return true if the client was not already subscribed
    */
   public synchronized boolean subscribe(final int clientId, final boolean once) {
      Subscriber sub = subscribers.get(clientId);
      if (sub == null) {
         sub = new Subscriber(clientId, 1);
         subscribers.put(clientId, sub);
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
      return ((long) (ownerId) << 32) | topicId;
   }

   public synchronized void queue(final Runnable task) {
      queue.queue(task);
   }

   public synchronized boolean isQueueEmpty() {
      return queue == null ? true : queue.isQueueEmpty();
   }

   /**
    * Process the pending work queued for this entity.
    * 
    * @return true if any queued work was processed.
    */
   public boolean process() {
      synchronized (this) {
         if (queue == null) {
            return false;
         }
      }
      return queue.process();
   }

}
