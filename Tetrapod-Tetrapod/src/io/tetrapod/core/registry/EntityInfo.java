package io.tetrapod.core.registry;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.tetrapod.core.ServiceCache;
import io.tetrapod.core.Session;
import io.tetrapod.core.utils.SequentialWorkQueue;
import io.tetrapod.protocol.core.Core;
import io.tetrapod.protocol.core.Entity;

/**
 * All the meta data associated with a tetrapod entity
 */
public class EntityInfo extends Entity implements Comparable<EntityInfo> {

   public static final Logger            logger      = LoggerFactory.getLogger(EntityInfo.class);

   /**
    * This entity's published topics
    * 
    * Maps topicId => Topic
    */
   protected Map<Integer, RegistryTopic> topics;

   /**
    * This entity's subscriptions
    * 
    * Maps topic key => Topic
    */
   protected Map<Long, RegistryTopic>    subscriptions;

   protected Session                     session;

   /**
    * An alternate not-necessarily-unique ID. This can be set by a service and used as a broadcast target. This is only set on the tetrapod
    * that owns the entity.
    */
   protected final AtomicInteger         alternateId = new AtomicInteger(0);

   protected Long                        lastContact;
   protected Long                        goneSince;

   protected SequentialWorkQueue         queue;

   public EntityInfo() {}

   public EntityInfo(Entity e) {
      this(e.entityId, e.parentId, e.reclaimToken, e.host, e.status, e.type, e.name, e.version, e.contractId, e.build);
      lastContact = System.currentTimeMillis();
   }

   public EntityInfo(int entityId, int parentId, long reclaimToken, String host, int status, byte type, String name, int version,
            int contractId, String build) {
      super(entityId, parentId, reclaimToken, host, status, type, name, version, contractId, build);
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

   /**
    * Returns true if this is a client
    */
   public boolean isClient() {
      return type == Core.TYPE_CLIENT;
   }

   public boolean isPaused() {
      return (status & Core.STATUS_PAUSED) != 0;
   }

   public boolean isGone() {
      return (status & Core.STATUS_GONE) != 0;
   }

   public synchronized RegistryTopic getTopic(int topicId) {
      return topics == null ? null : topics.get(topicId);
   }

   public synchronized RegistryTopic publish(int topicId) {
      final RegistryTopic topic = new RegistryTopic(entityId, topicId);
      if (topics == null) {
         topics = new HashMap<>();
      }
      topics.put(topic.topicId, topic);
      //logger.debug("======= PUBLISHED {} : {}", this, topic);
      return topic;
   }

   public synchronized RegistryTopic unpublish(int topicId) {
      if (topics != null) {
         return topics.remove(topicId);
      } else {
         return null;
      }
   }

   public synchronized void subscribe(RegistryTopic topic) {
      if (subscriptions == null) {
         subscriptions = new HashMap<>();
      }
      subscriptions.put(topic.key(), topic);
      //logger.debug("======= SUBSCRIBED {} to {}", this, topic);
   }

   public synchronized void unsubscribe(RegistryTopic topic) {
      if (subscriptions != null) {
         subscriptions.remove(topic.key());
      }
   }

   public synchronized List<RegistryTopic> getTopics() {
      final List<RegistryTopic> list = new ArrayList<RegistryTopic>();
      if (topics != null) {
         list.addAll(topics.values());
      }
      return list;
   }

   public synchronized List<RegistryTopic> getSubscriptions() {
      final List<RegistryTopic> list = new ArrayList<RegistryTopic>();
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

   public synchronized long getGoneSince() {
      if (goneSince == null) {
         goneSince = System.currentTimeMillis();
      }
      return goneSince;
   }

   @Override
   public String toString() {
      return String.format("Entity-0x%08X (%s)", entityId, name);
   }

   public void setSession(Session ses) {
      this.session = ses;
      this.lastContact = null;
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
         queue = new SequentialWorkQueue();
      }
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

   public boolean isPendingRegistration() {
      return session == null && lastContact != null && !isGone();
   }

   public boolean hasConnectedSession() {
      return session != null && session.isConnected();
   }

}
