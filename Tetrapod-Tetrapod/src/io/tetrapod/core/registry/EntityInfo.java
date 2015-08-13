package io.tetrapod.core.registry;

import io.tetrapod.core.*;
import io.tetrapod.protocol.core.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;

import org.slf4j.*;

/**
 * All the meta data associated with a tetrapod entity
 */
public class EntityInfo extends Entity implements Comparable<EntityInfo> {

   public static final Logger    logger      = LoggerFactory.getLogger(EntityInfo.class);

   protected int                 topicCounter;

   /**
    * This entity's published topics
    * 
    * Maps topicId => Topic
    */
   protected Map<Integer, Topic> topics;

   /**
    * This entity's subscriptions
    * 
    * Maps topic key => Topic
    */
   protected Map<Long, Topic>    subscriptions;

   protected Session             session;

   /**
    * An alternate not-necessarily-unique ID. This can be set by a service and used as a broadcast target. This is only set on the tetrapod
    * that owns the entity.
    */
   protected final AtomicInteger alternateId = new AtomicInteger(0);

   protected Long                lastContact;
   protected Long                goneSince;

   protected Lock                consumerLock;
   protected Queue<Runnable>     queue;

   public EntityInfo() {}

   public EntityInfo(Entity e) {
      this(e.entityId, e.parentId, e.reclaimToken, e.host, e.status, e.type, e.name, e.build, e.version, e.contractId);
   }

   public EntityInfo(int entityId, int parentId, long reclaimToken, String host, int status, byte type, String name, int build,
         int version, int contractId) {
      super(entityId, parentId, reclaimToken, host, status, type, name, build, version, contractId);
   }

   public boolean isTetrapod() {
      return type == Core.TYPE_TETRAPOD;
   }

   /**
    * Returns true if this is a service, including tetrapod services
    */
   public boolean isService() {
      return type == Core.TYPE_SERVICE || type == Core.TYPE_TETRAPOD;
   }

   /**
    * Returns true if this is an admin
    */
   public boolean isAdmin() {
      return type == Core.TYPE_ADMIN;
   }

   public boolean isPaused() {
      return (status & Core.STATUS_PAUSED) != 0;
   }

   public boolean isGone() {
      return (status & Core.STATUS_GONE) != 0;
   }

   public synchronized Topic getTopic(int topicId) {
      return topics == null ? null : topics.get(topicId);
   }

   public synchronized Topic publish() {
      return publish(nextTopicId());
   }

   public synchronized int nextTopicId() {
      return ++topicCounter;
   }

   public synchronized Topic publish(int topicId) {
      if (topicCounter != topicId) {
         //assert (false);
         logger.warn("TopicIds don't match! {} != {}", topicCounter, topicId);
         topicCounter = topicId;
      }

      final Topic topic = new Topic(entityId, topicId);
      if (topics == null) {
         topics = new HashMap<>();
      }
      topics.put(topic.topicId, topic);
      //logger.debug("======= PUBLISHED {} : {}", this, topic);
      return topic;
   }

   public synchronized Topic unpublish(int topicId) {
      if (topics != null) {
         return topics.remove(topicId);
      } else {
         return null;
      }
   }

   public synchronized void subscribe(Topic topic) {
      if (subscriptions == null) {
         subscriptions = new HashMap<>();
      }
      subscriptions.put(topic.key(), topic);
      //logger.debug("======= SUBSCRIBED {} to {}", this, topic);
   }

   public synchronized void unsubscribe(Topic topic) {
      if (subscriptions != null) {
         subscriptions.remove(topic.key());
      }
   }

   public synchronized List<Topic> getTopics() {
      final List<Topic> list = new ArrayList<Topic>();
      if (topics != null) {
         list.addAll(topics.values());
      }
      return list;
   }

   public synchronized List<Topic> getSubscriptions() {
      final List<Topic> list = new ArrayList<Topic>();
      if (subscriptions != null) {
         list.addAll(subscriptions.values());
      }
      return list;
   }

   public synchronized int getNumTopics() {
      return topics == null ? 0 : topics.size();
   }

   public synchronized int getNumSubscriptions() {
      return subscriptions == null ? 0 : subscriptions.size();
   }

   @Override
   public int compareTo(EntityInfo o) {
      return entityId - o.entityId;
   }

   public synchronized void setStatus(int status) {
      this.status = status;
      if (isGone()) {
         if (goneSince == null) {
            goneSince = System.currentTimeMillis();
         }
      } else {
         goneSince = null;
      }
   }

   public long getGoneSince() {
      return goneSince;
   }

   @Override
   public String toString() {
      return String.format("Entity-0x%08X (%s)", entityId, name);
   }

   public void setSession(Session ses) {
      this.session = ses;
   }

   public Session getSession() {
      return session;
   }

   /**
    * Returns true if this service is considered available. Checks all status bits that might cause unavailability
    * 
    * @return
    */
   public boolean isAvailable() {
      return ServiceCache.isAvailable(this.status);
   }

   public synchronized void queue(final Runnable task) {
      if (queue == null) {
         queue = new ConcurrentLinkedQueue<>();
         consumerLock = new ReentrantLock();
      }
      queue.add(task);
   }

   public synchronized boolean isQueueEmpty() {
      return queue == null ? true : queue.isEmpty();
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
      boolean processedSomething = false;
      if (consumerLock.tryLock()) {
         try {
            Runnable task = null;
            do {
               task = queue.poll();
               if (task != null) {
                  processedSomething = true;
                  try {
                     task.run();
                  } catch (Throwable e) {
                     logger.error(e.getMessage(), e);
                  }
               }
            } while (task != null);
         } finally {
            consumerLock.unlock();
         }
      }
      return processedSomething;
   }

   public synchronized int getAlternateId() {
      return alternateId.get();
   }

   public synchronized void setAlternateId(int id) {
      alternateId.set(id);
   }

   public synchronized void setLastContact(Long val) {
      this.lastContact = val;
   }

   public synchronized Long getLastContact() {
      return lastContact;
   }

}
