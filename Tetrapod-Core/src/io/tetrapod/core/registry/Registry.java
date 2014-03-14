package io.tetrapod.core.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The global registry of all current actors in the system and their published topics and subscriptions
 * 
 * Each tetrapod service owns a shard of the registry and has a full replica of all other shards.
 */
public class Registry {
   private static final int               MAX_ID   = 0x000FFFFF;

   private final int                      parentId;
   private int                            counter;

   private final Map<Integer, Actor>      clients  = new ConcurrentHashMap<>();
   private final Map<Integer, ServiceDef> services = new ConcurrentHashMap<>();

   public Registry(int parentId) {
      this.parentId = parentId;
      counter = 0;
   }

   public void register(Actor thingy) {
      thingy.actorId = issueId();
      clients.put(thingy.actorId, thingy);
   }

   public void unregister(Actor thingy) {
      // TODO: Unpublish all their topics
      // TODO: Unsubscribe from all subscriptions
      clients.remove(thingy.actorId);
   }

   /**
    * @return a new unused ID. If we hit our local maximum, we will reset and find the next currently unused id
    */
   private synchronized int issueId() {
      while (true) {
         int id = ++counter % MAX_ID;
         if (!clients.containsKey(id)) {
            return parentId | id;
         }
      }
   }
}
