package io.tetrapod.core.registry;

import io.tetrapod.core.*;
import io.tetrapod.protocol.core.*;

import java.util.*;
import java.util.concurrent.locks.*;

import org.slf4j.*;

/**
 * All the meta data associated with a tetrapod entity
 */
public class EntityInfo extends Entity implements Comparable<EntityInfo> {

   public static final Logger    logger = LoggerFactory.getLogger(EntityInfo.class);

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
      return publish(++topicCounter);
   }

   public synchronized Topic publish(int topicId) {
      if (++topicCounter != topicId) {
         //assert (false);
         logger.error("TopicIds don't match! {} != {}", topicCounter, topicId);
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

   public synchronized Collection<Topic> getTopics() {
      return topics == null ? new ArrayList<Topic>() : topics.values();
   }

   public synchronized Collection<Topic> getSubscriptions() {
      return subscriptions == null ? new ArrayList<Topic>(0) : subscriptions.values();
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
      return (status & (Core.STATUS_STARTING | Core.STATUS_PAUSED | Core.STATUS_GONE | Core.STATUS_BUSY | Core.STATUS_OVERLOADED
            | Core.STATUS_FAILED | Core.STATUS_STOPPING)) == 0;
   }

   public synchronized void queue(final Runnable task) {
      if (queue == null) {
         queue = new LinkedList<Runnable>();
         consumerLock = new ReentrantLock();
      }
      queue.add(task);
   }

   public synchronized int getQueueLength() {
      return queue == null ? 0 : queue.size();
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
      boolean res = false;
      if (consumerLock.tryLock()) {
         try {
            Runnable task = null;
            do {
               task = queue.poll();
               if (task != null) {
                  try {
                     task.run();
                  } catch (Throwable e) {
                     logger.error(e.getMessage(), e);
                  }
                  res = true;
               }
            } while (task != null);
         } finally {
            consumerLock.unlock();
         }
      }
      return res;
   }

}
