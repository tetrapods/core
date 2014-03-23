package io.tetrapod.core.registry;

import io.tetrapod.protocol.core.*;

import java.util.*;

/**
 * All the meta data associated with a tetrapod entity
 */
public class EntityInfo extends Entity {

   // TODO: stats

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
      return topics.remove(topicId);
   }

   public synchronized Collection<Topic> getTopics() {
      return topics.values();
   }

   public synchronized Collection<Topic> getSubscriptions() {
      return subscriptions.values();
   }

}
