package io.tetrapod.core.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The global registry of all current actors in the system and their published topics and subscriptions
 * 
 * Each tetrapod service owns a shard of the registry and has a full replica of all other shards.
 */
public class Registry {
   public static final int                PARENT_ID_MASK = 0x7FF00000;               // top bytes denotes the parent
   private static final int               MAX_ID         = 0x000FFFFF;

   private final int                      parentId;
   private int                            counter;

   private final Map<Integer, EntityInfo> entities       = new ConcurrentHashMap<>();
   private final Map<Integer, ServiceDef> services       = new ConcurrentHashMap<>();

   public Registry(int parentId) {
      this.parentId = parentId;
      counter = 0;
   }

   public void register(EntityInfo entity) {
      entity.entityId = issueId();
      entities.put(entity.entityId, entity);
   }

   public void unregister(EntityInfo thingy) {
      // TODO: Unpublish all their topics
      // TODO: Unsubscribe from all subscriptions
      entities.remove(thingy.entityId);
   }

   /**
    * @return a new unused ID. If we hit our local maximum, we will reset and find the next currently unused id
    */
   private synchronized int issueId() {
      while (true) {
         int id = ++counter % MAX_ID;
         if (!entities.containsKey(id)) {
            return parentId | id;
         }
      }
   }

   public EntityInfo getEntity(int entityId) {
      return entities.get(entityId);
   }
}
