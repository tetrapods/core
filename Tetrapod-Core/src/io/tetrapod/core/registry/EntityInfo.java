package io.tetrapod.core.registry;

import io.tetrapod.protocol.core.*;

import java.util.*;

/**
 * All the meta data associated with a tetrapod entity
 */
public class EntityInfo extends Entity {

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

   public EntityInfo() {}

   public EntityInfo(Entity e) {
      this(e.entityId, e.parentId, e.reclaimToken, e.host, e.status, e.type, e.name, e.build, e.version);
   }

   public EntityInfo(int entityId, int parentId, long reclaimToken, String host, int status, byte type, String name, int build, int version) {
      super(entityId, parentId, reclaimToken, host, status, type, name, build, version);
   }

   public boolean isTetrapod() {
      return type == Core.TYPE_TETRAPOD;
   }

   public boolean isService() {
      return type == Core.TYPE_SERVICE;
   }

   public synchronized Topic getTopic(int topicId) {
      return topics == null ? null : topics.get(topicId);
   }

   public synchronized Topic publish() {
      final Topic topic = new Topic(entityId, ++topicCounter);
      if (topics == null) {
         topics = new HashMap<>();
      }
      topics.put(topic.topicId, topic);
      return topic;
   }

   public synchronized Topic unpublish(int topicId) {
      if (topics != null) {
         return topics.remove(topicId);
      } else {
         return null;
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

}
