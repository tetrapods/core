package io.tetrapod.core.registry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.*;

/**
 * The global registry of all current actors in the system and their published topics and subscriptions
 * 
 * Each tetrapod service owns a shard of the registry and has a full replica of all other shards.
 */
public class Registry {

   public static final Logger                   logger         = LoggerFactory.getLogger(Registry.class);

   public static final int                      PARENT_ID_MASK = 0x7FF00000;                             // top bytes denotes the parent
   private static final int                     MAX_ID         = 0x000FFFFF;

   /**
    * Our entityId
    */
   private int                                  parentId;

   /**
    * Our local entity id counter
    */
   private int                                  counter;

   /**
    * Maps entityId => EntityInfo
    */
   private final Map<Integer, EntityInfo>       entities       = new ConcurrentHashMap<>();

   /**
    * Maps contractId => List of EntityInfos that provide that service
    */
   private final Map<Integer, List<EntityInfo>> services       = new ConcurrentHashMap<>();

   public Registry() {}

   public synchronized void setParentId(int id) {
      this.parentId = id;
   }

   public synchronized void register(EntityInfo entity) {
      if (entity.entityId <= 0) {
         entity.entityId = issueId();
      }
      entities.put(entity.entityId, entity);
      if (entity.isService()) {
         // register their service in our services list
      }
   }

   public synchronized void unregister(EntityInfo entity) {
      // Unpublish all their topics
      for (Topic topic : entity.getTopics()) {
         unpublish(entity, topic.topicId);
      }
      // Unsubscribe from all subscriptions
      for (Topic topic : entity.getSubscriptions()) {
         unsubscribe(entity, topic);
      }

      entities.remove(entity.entityId);
   }

   /**
    * @return a new unused ID. If we hit our local maximum, we will reset and find the next currently unused id
    */
   private synchronized int issueId() {
      while (true) {
         int id = (parentId << 20) | (++counter % MAX_ID);
         if (!entities.containsKey(id)) {
            return id;
         }
      }
   }

   public EntityInfo getEntity(int entityId) {
      return entities.get(entityId);
   }

   public Topic publish(int entityId) {
      final EntityInfo entity = getEntity(entityId);
      if (entity != null) {
         return entity.publish();
      } else {
         logger.error("Could not find entity {}", entity);
      }
      return null;
   }

   public boolean unpublish(int entityId, int topicId) {
      final EntityInfo entity = getEntity(entityId);
      if (entity != null) {
         return unpublish(entity, topicId);
      } else {
         logger.error("Could not find entity {}", entity);
      }
      return false;
   }

   public boolean unpublish(EntityInfo entity, int topicId) {
      final Topic topic = entity.unpublish(topicId);
      if (topic != null) {
         // clean up all the subscriptions to this topic
         for (Subscriber sub : topic.getSubscribers()) {
            final EntityInfo e = getEntity(sub.id);
            if (e != null) {
               unsubscribe(e, topic);
            }
         }
         return true;
      }
      return false;
   }

   public void subscribe(EntityInfo entity, Topic topic) {
      if (entity.parentId == parentId) {
         // it's our child, so directly subscribe them
         topic.subscribe(entity.entityId);
      } else {
         // just subscribe their parent as proxy
         topic.subscribe(entity.parentId);
      }
   }

   public boolean unsubscribe(EntityInfo entity, Topic topic) {
      if (entity.parentId == parentId) {
         // unsubscribe them directly
         return topic.unsubscribe(entity.entityId, true);
      } else {
         // unsubscribe the parent subscription which will decrement their counter
         return topic.unsubscribe(entity.parentId, false);
         // FIXME: there's a minor bug here if they subscribed more than once
      }
   }

}
