package io.tetrapod.core.registry;

import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.*;

import java.util.*;
import java.util.concurrent.*;

import org.slf4j.*;

/**
 * The global registry of all current actors in the system and their published topics and subscriptions
 * 
 * Each tetrapod service owns a shard of the registry and has a full replica of all other shards.
 */
public class Registry implements TetrapodContract.Registry.API {

   protected static final Logger                logger          = LoggerFactory.getLogger(Registry.class);

   public static final int                      MAX_PARENTS     = 0x000007FF;
   public static final int                      MAX_ID          = 0x000FFFFF;

   public static final int                      PARENT_ID_SHIFT = 20;
   public static final int                      PARENT_ID_MASK  = MAX_PARENTS << PARENT_ID_SHIFT;
   public static final int                      BOOTSTRAP_ID    = 1 << PARENT_ID_SHIFT;

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
   private final Map<Integer, EntityInfo>       entities        = new ConcurrentHashMap<>();

   /**
    * Maps contractId => List of EntityInfos that provide that service
    */
   private final Map<Integer, List<EntityInfo>> services        = new ConcurrentHashMap<>();

   public static interface RegistryBroadcaster {
      public void broadcastRegistryMessage(Message msg);

      public void broadcastServicesMessage(Message msg);
   }

   private final RegistryBroadcaster broadcaster;

   public Registry(RegistryBroadcaster broadcaster) {
      this.broadcaster = broadcaster;
   }

   public synchronized void setParentId(int id) {
      this.parentId = id;
   }

   public synchronized int getParentId() {
      return parentId;
   }

   public Collection<EntityInfo> getEntities() {
      return entities.values();
   }

   public List<EntityInfo> getChildren() {
      final List<EntityInfo> children = new ArrayList<>();
      for (EntityInfo e : entities.values()) {
         if (e.parentId == parentId && e.entityId != parentId) {
            children.add(e);
         }
      }
      return children;
   }

   public List<EntityInfo> getServices() {
      final List<EntityInfo> list = new ArrayList<>();
      for (EntityInfo e : entities.values()) {
         if (e.isService()) {
            list.add(e);
         }
      }
      return list;
   }

   public synchronized void register(EntityInfo entity) {
      if (entity.entityId <= 0) {
         if (entity.isTetrapod()) {
            entity.entityId = issueTetrapodId();
         } else {
            entity.entityId = issueId();
         }
      }

      entities.put(entity.entityId, entity);
      if (entity.isService()) {
         // register their service in our services list
         ensureServicesList(entity.contractId).add(entity);
      }
      if (entity.parentId == parentId && entity.entityId != parentId && broadcaster != null) {
         broadcaster.broadcastRegistryMessage(new EntityRegisteredMessage(entity));
      }
      if (entity.isService()) {
         broadcaster.broadcastServicesMessage(new ServiceAddedMessage(entity));
      }
   }

   public EntityInfo getEntity(int entityId) {
      return entities.get(entityId);
   }

   public EntityInfo getFirstAvailableService(int contractId) {
      // Using a CopyOnWrite list this method doesn't need to lock
      final List<EntityInfo> list = services.get(contractId);
      if (list != null) {
         final ListIterator<EntityInfo> li = list.listIterator();
         while (li.hasNext()) {
            final EntityInfo info = li.next();
            if (info.isAvailable()) {
               return info;
            }
         }
      }
      return null;
   }

   public EntityInfo getRandomAvailableService(int contractId) {
      // Using a CopyOnWrite list this method doesn't need to lock
      final List<EntityInfo> list = services.get(contractId);
      if (list != null) {
         final List<EntityInfo> shuffled = new ArrayList<>(list);
         Collections.shuffle(shuffled);
         for (EntityInfo info : shuffled) {
            if (info != null && info.isAvailable()) {
               return info;
            }
         }
      }
      return null;
   }

   public synchronized void unregister(final EntityInfo e) {

      // Unpublish all their topics
      for (Topic topic : e.getTopics()) {
         unpublish(e, topic.topicId);
      }
      // Unsubscribe from all subscriptions
      for (Topic topic : e.getSubscriptions()) {
         EntityInfo owner = getEntity(topic.ownerId);
         unsubscribe(owner, topic.topicId, e.entityId, true);
      }

      entities.remove(e.entityId);

      if (e.parentId == parentId) {
         broadcaster.broadcastRegistryMessage(new EntityUnregisteredMessage(e.entityId));
      }
      if (e.isService()) {
         broadcaster.broadcastServicesMessage(new ServiceRemovedMessage(e.entityId));
      }

      if (e.isService()) {
         List<EntityInfo> list = services.get(e.contractId);
         if (list != null)
            list.remove(e);
      }

   }

   /**
    * @return a new unused ID. If we hit our local maximum, we will reset and find the next currently unused id
    */
   private synchronized int issueId() {
      while (true) {
         int id = parentId | (++counter % MAX_ID);
         if (!entities.containsKey(id)) {
            return id;
         }
      }
   }

   /**
    * Issue the next available tetrapod id
    * 
    * FIXME: This is currently _very_ unsafe, but will do until we have the robust distributed counters or locks implemented
    */
   public synchronized int issueTetrapodId() {
      int nextId = parentId >> PARENT_ID_SHIFT;
      while (true) {
         int id = (++nextId % MAX_PARENTS) << PARENT_ID_SHIFT;
         if (!entities.containsKey(id)) {
            return id;
         }
      }
   }

   public void updateStatus(final EntityInfo e, int status) {
      e.setStatus(status);
      if (e.parentId == parentId) {
         broadcaster.broadcastRegistryMessage(new EntityUpdatedMessage(e.entityId, status));
      }
      if (e.isService()) {
         broadcaster.broadcastServicesMessage(new ServiceUpdatedMessage(e.entityId, status));
      }
   }

   public Topic publish(int entityId) {
      final EntityInfo e = getEntity(entityId);
      if (e != null) {
         Topic topic = e.publish();
         if (e.parentId == parentId) {
            broadcaster.broadcastRegistryMessage(new TopicPublishedMessage(e.entityId, topic.topicId));
         }
         return topic;
      } else {
         logger.error("Could not find entity {}", e);
      }
      return null;
   }

   public boolean unpublish(EntityInfo e, int topicId) {
      final Topic topic = e.unpublish(topicId);
      if (topic != null) {
         // clean up all the subscriptions to this topic
         for (Subscriber sub : topic.getSubscribers()) {
            unsubscribe(e, topicId, sub.entityId, true);
         }
         if (e.parentId == parentId) {
            broadcaster.broadcastRegistryMessage(new TopicUnpublishedMessage(e.entityId, topicId));
         }
         return true;
      }
      return false;
   }

   public void subscribe(final EntityInfo publisher, final int topicId, final int entityId) {
      final Topic topic = publisher.getTopic(topicId);
      if (topic != null) {
         final EntityInfo e = getEntity(entityId);
         if (e != null) {
            if (e.parentId == parentId) {
               // it's our child, so directly subscribe them
               topic.subscribe(e.entityId);
            } else {
               // just subscribe their parent as proxy
               topic.subscribe(e.parentId);
            }
            e.subscribe(topic);

            if (publisher.parentId == parentId) {
               broadcaster.broadcastRegistryMessage(new TopicSubscribedMessage(topic.ownerId, topic.topicId, e.entityId));
            }
         } else {
            logger.info("Could not find subscriber {} for topic {}", entityId, topicId);
         }
      } else {
         logger.info("Could not find topic {} for {}", topicId, publisher);
      }
   }

   public void unsubscribe(final EntityInfo publisher, final int topicId, final int entityId, final boolean all) {
      final Topic topic = publisher.getTopic(topicId);
      if (topic != null) {
         final EntityInfo entity = getEntity(entityId);
         if (entity != null) {
            if (entity.parentId == parentId) {
               // unsubscribe them directly
               if (topic.unsubscribe(entity.entityId, all)) {
                  entity.unsubscribe(topic);
               }
            } else {
               // unsubscribe the parent subscription  
               if (topic.unsubscribe(entity.parentId, false)) {
                  // FIXME: there's a minor bug here if they subscribed more than once
                  entity.unsubscribe(topic);
               }
            }

            if (publisher.parentId == parentId) {
               broadcaster.broadcastRegistryMessage(new TopicUnsubscribedMessage(publisher.entityId, topicId, entityId));
            }

         } else {
            logger.info("Could not find subscriber {} for topic {}", entityId, topicId);
         }
      } else {
         logger.info("Could not find topic {} for {}", topicId, publisher);
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public void genericMessage(Message message, MessageContext ctx) {}

   @Override
   public void messageEntityRegistered(EntityRegisteredMessage m, MessageContext ctx) {
      // TODO: validate sender    
      if (ctx.header.fromId != parentId) {
         EntityInfo info = entities.get(m.entity.entityId);
         if (info != null) {
            info.parentId = m.entity.parentId;
            info.reclaimToken = m.entity.reclaimToken;
            info.host = m.entity.host;
            info.status = m.entity.status;
            info.build = m.entity.build;
            info.name = m.entity.name;
            info.version = m.entity.version;
            info.contractId = m.entity.contractId;
         } else {
            info = new EntityInfo(m.entity);
         }
         register(info);
      }
   }

   @Override
   public void messageEntityUnregistered(EntityUnregisteredMessage m, MessageContext ctx) {
      // TODO: validate sender           
      if (ctx.header.fromId != parentId) {
         final EntityInfo e = getEntity(m.entityId);
         if (e != null) {
            e.queue(new Runnable() {
               public void run() {
                  unregister(e);
               }
            });
         } else {
            logger.error("Could not find entity {} to unregister", m.entityId);
         }
      }
   }

   @Override
   public void messageEntityUpdated(final EntityUpdatedMessage m, MessageContext ctx) {
      // TODO: validate sender           
      if (ctx.header.fromId != parentId) {
         final EntityInfo e = getEntity(m.entityId);
         if (e != null) {
            e.queue(new Runnable() {
               public void run() {
                  updateStatus(e, m.status);
               }
            });
         } else {
            logger.error("Could not find entity {} to update", m.entityId);
         }
      }
   }

   @Override
   public void messageTopicPublished(final TopicPublishedMessage m, MessageContext ctx) {
      // TODO: validate sender
      if (ctx.header.fromId != parentId) {
         final EntityInfo owner = getEntity(m.ownerId);
         if (owner != null) {
            owner.queue(new Runnable() {
               public void run() {
                  final Topic topic = owner.publish();
                  if (topic.topicId != m.topicId) {
                     logger.error("TopicIDs don't match! {} != {}", topic, m.topicId);
                     assert (false);
                  }
               }
            }); // TODO: kick()
         } else {
            logger.info("Could not find publisher entity {}", m.ownerId);
         }
      }
   }

   @Override
   public void messageTopicUnpublished(final TopicUnpublishedMessage m, MessageContext ctx) {
      // TODO: validate sender
      if (ctx.header.fromId != parentId) {
         final EntityInfo owner = getEntity(m.ownerId);
         if (owner != null) {
            owner.queue(new Runnable() {
               public void run() {
                  final Topic topic = owner.unpublish(m.topicId);
                  if (topic == null) {
                     logger.info("Could not find topic {} for entity {}", m.topicId, m.ownerId);
                  }
               }
            }); // TODO: kick()
         } else {
            logger.info("Could not find publisher entity {}", m.ownerId);
         }
      }
   }

   @Override
   public void messageTopicSubscribed(final TopicSubscribedMessage m, MessageContext ctx) {
      // TODO: validate sender 
      if (ctx.header.fromId != parentId) {
         final EntityInfo owner = getEntity(m.ownerId);
         if (owner != null) {
            owner.queue(new Runnable() {
               public void run() {
                  subscribe(owner, m.topicId, m.entityId);
               }
            }); // TODO: kick() 
         } else {
            logger.info("Could not find publisher entity {}", m.ownerId);
         }
      }
   }

   @Override
   public void messageTopicUnsubscribed(final TopicUnsubscribedMessage m, MessageContext ctx) {
      if (ctx.header.fromId != parentId) {
         // TODO: validate sender           
         final EntityInfo owner = getEntity(m.ownerId);
         if (owner != null) {
            owner.queue(new Runnable() {
               public void run() {
                  unsubscribe(owner, m.topicId, m.entityId, false);
               }
            }); // TODO: kick()
         } else {
            logger.info("Could not find publisher entity {}", m.ownerId);
         }
      }
   }

   @Override
   public void messageEntityListComplete(EntityListCompleteMessage m, MessageContext ctx) {
      logger.info("====================== SYNCED {} ======================", ctx.header.fromId);
   }

   //////////////////////////////////////////////////////////////////////////////////////////

   public void logStats() {
      List<EntityInfo> list = new ArrayList<>(entities.values());
      Collections.sort(list);
      logger.info("========================== TETRAPOD CLUSTER REGISTRY ============================");
      for (EntityInfo e : list) {
         logger.info(String.format(" 0x%08X 0x%08X %-15s status=%08X topics=%d subscriptions=%d", e.parentId, e.entityId, e.name, e.status,
               e.getNumTopics(), e.getNumSubscriptions()));
      }
      logger.info("=================================================================================\n");

   }

   private List<EntityInfo> ensureServicesList(int contractId) {
      List<EntityInfo> list = services.get(contractId);
      if (list == null) {
         list = new CopyOnWriteArrayList<>();
         services.put(contractId, list);
      }
      return list;
   }

}
