package io.tetrapod.core.registry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.tetrapod.core.Session;
import io.tetrapod.core.rpc.Message;
import io.tetrapod.core.rpc.MessageContext;
import io.tetrapod.core.storage.*;
import io.tetrapod.core.web.LongPollQueue;
import io.tetrapod.protocol.core.*;
import io.tetrapod.raft.Entry;
import io.tetrapod.raft.RaftRPC.ClientResponseHandler;

/**
 * The global registry of all current actors in the system and their published topics and subscriptions
 * 
 * Each tetrapod service owns a shard of the registry and has a full replica of all other shards.
 */
@Deprecated
public class Registry implements TetrapodContract.Registry.API {

   protected static final Logger                logger   = LoggerFactory.getLogger(Registry.class);

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
   private final Map<Integer, EntityInfo>       entities = new ConcurrentHashMap<>();

   /**
    * Maps contractId => List of EntityInfos that provide that service
    */
   private final Map<Integer, List<EntityInfo>> services = new ConcurrentHashMap<>();

   public static interface RegistryBroadcaster {
      public void broadcastServicesMessage(Message msg);
      public void sendMessage(Message msg, int toEntity);
   }

   private final RegistryBroadcaster broadcaster;
   private final TetrapodCluster     cluster;

   public Registry(RegistryBroadcaster broadcaster, TetrapodCluster cluster) {
      this.broadcaster = broadcaster;
      this.cluster = cluster;
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
      for (EntityInfo e : getEntities()) {
         if (e.parentId == parentId && e.entityId != parentId) {
            children.add(e);
         }
      }
      return children;
   }

   public List<EntityInfo> getServices() {
      final List<EntityInfo> list = new ArrayList<>();
      for (EntityInfo e : getEntities()) {
         if (e.isService()) {
            list.add(e);
         }
      }
      return list;
   }

   public EntityInfo getEntity(int entityId) {
      return entities.get(entityId); 
   }

   public EntityInfo getFirstAvailableService(int contractId) {
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

   /**
    * @return a new unused ID. If we hit our local maximum, we will reset and find the next currently unused id
    */
   public synchronized int issueId() {
      while (true) {
         int id = parentId | (++counter % TetrapodContract.MAX_ID);
         if (getEntity(id) == null) {
            return id;
         }
      }
   }

   private void clearAllTopicsAndSubscriptions(final EntityInfo e) {
      logger.debug("clearAllTopicsAndSubscriptions: {}", e);
      // Unpublish all their topics
      for (RegistryTopic topic : e.getTopics()) {
         unpublish(e, topic.topicId);
      }
      // Unsubscribe from all subscriptions
      for (RegistryTopic topic : e.getSubscriptions()) {
         EntityInfo owner = getEntity(topic.ownerId);
         // assert (owner != null); 
         if (owner != null) {
            unsubscribe(owner, topic.topicId, e.entityId, true);
            
            // notify the publisher that this client's subscription is now dead
            broadcaster.sendMessage(new TopicUnsubscribedMessage(topic.topicId, e.entityId), owner.entityId);
            
         } else {
            // bug here cleaning up topics on unreg, I think...
            logger.warn("clearAllTopicsAndSubscriptions: Couldn't find {} owner {}", topic, topic.ownerId);
         }
      }
   }

   public void unregister(final EntityInfo e) {
      cluster.executeCommand(new DelEntityCommand(e.entityId), new ClientResponseHandler<TetrapodStateMachine>() {
         @Override
         public void handleResponse(Entry<TetrapodStateMachine> entry) {}
      });
   }

   public void updateStatus(EntityInfo e, int status) {
      cluster.executeCommand(new ModEntityCommand(e.entityId, status, e.build, e.version), null);
   }

   public RegistryTopic publish(int entityId, int topicId) {
      final EntityInfo e = getEntity(entityId);
      if (e != null) {
         return e.publish(topicId);
      } else {
         logger.error("Could not find entity {}", e);
      }
      return null;
   }

   public boolean unpublish(EntityInfo e, int topicId) {
      final RegistryTopic topic = e.unpublish(topicId);
      if (topic != null) {
         // clean up all the subscriptions to this topic
         synchronized (topic) {
            for (Subscriber sub : topic.getSubscribers().toArray(new Subscriber[0])) {
               unsubscribe(e, topic, sub.entityId, true);
            }
         }
         return true;
      } else {
         logger.info("Could not find topic {} for entity {}", topicId, e);
      }
      return false;
   }

   public void subscribe(final EntityInfo publisher, final int topicId, final int entityId, final boolean once) {
      final RegistryTopic topic = publisher.getTopic(topicId);
      if (topic != null) {
         final EntityInfo e = getEntity(entityId);
         if (e != null) {
            topic.subscribe(publisher, e, once);
            e.subscribe(topic);
         } else {
            logger.info("Could not find subscriber {} for topic {}", entityId, topicId);
         }
      } else {
         logger.info("Could not find topic {} for {}", topicId, publisher);
      }
   }

   public void unsubscribe(final EntityInfo publisher, final int topicId, final int entityId, final boolean all) {
      assert (publisher != null);
      final RegistryTopic topic = publisher.getTopic(topicId);
      if (topic != null) {
         unsubscribe(publisher, topic, entityId, all);
      } else {
         logger.info("Could not find topic {} for {}", topicId, publisher);
      }
   }

   public void unsubscribe(final EntityInfo publisher, RegistryTopic topic, final int entityId, final boolean all) {
      assert (publisher != null);
      assert (topic != null);
      if (topic.unsubscribe(entityId, all)) {
         final EntityInfo e = getEntity(entityId);
         if (e != null) {
            e.unsubscribe(topic);
         }
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public void genericMessage(Message message, MessageContext ctx) {}

   @Override
   public void messageTopicPublished(final TopicPublishedMessage m, MessageContext ctx) {
      // TODO: validate sender
      logger.info("******* {}", m.dump());
      final EntityInfo owner = getEntity(ctx.header.fromId);
      if (owner != null) {
         owner.queue(new Runnable() {
            public void run() {
               owner.publish(m.topicId);
            }
         }); // TODO: kick()
      } else {
         logger.info("Could not find publisher entity {}", ctx.header.fromId);
      }
   }

   @Override
   public void messageTopicUnpublished(final TopicUnpublishedMessage m, MessageContext ctx) {
      logger.info("******* {}", m.dump());
      final EntityInfo owner = getEntity(ctx.header.fromId);
      if (owner != null) {
         owner.queue(new Runnable() {
            public void run() {
               unpublish(owner, m.topicId);
            }
         }); // TODO: kick()
      } else {
         logger.info("Could not find publisher entity {}", ctx.header.fromId);
      }
   }

   @Override
   public void messageTopicSubscribed(final TopicSubscribedMessage m, MessageContext ctx) {
      logger.info("******* {}", m.dump());
      final EntityInfo owner = getEntity(ctx.header.fromId);
      if (owner != null) {
         owner.queue(new Runnable() {
            public void run() {
               subscribe(owner, m.topicId, m.entityId, m.once);
            }
         }); // TODO: kick() 
      } else {
         logger.info("Could not find publisher entity {}", ctx.header.fromId);
      }
   }

   @Override
   public void messageTopicUnsubscribed(final TopicUnsubscribedMessage m, MessageContext ctx) {
      logger.info("******* {}", m.dump());
      final EntityInfo owner = getEntity(ctx.header.fromId);
      if (owner != null) {
         owner.queue(new Runnable() {
            public void run() {
               unsubscribe(owner, m.topicId, m.entityId, false);
            }
         }); // TODO: kick()
      } else {
         logger.info("Could not find publisher entity {}", ctx.header.fromId);
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////

   public synchronized void logStats(boolean includeClients) {
      List<EntityInfo> list = new ArrayList<>(getEntities());
      Collections.sort(list);
      logger.info("========================== TETRAPOD CLUSTER REGISTRY ============================");
      for (EntityInfo e : list) {
         if (includeClients || !e.isClient())
            logger.info(String.format(" 0x%08X 0x%08X %-15s status=%08X topics=%d subscriptions=%d", e.parentId, e.entityId, e.name,
                     e.status, e.getNumTopics(), e.getNumSubscriptions()));
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

   public void setGone(EntityInfo e) {

      if (e.getLastContact() != null) {
         // we set this value to non-null only for web-polling sessions, 
         // which need to be handled differently since multiple sessions can 
         // be in use for the same entity, we only set gone when health monitor 
         // nulls this value and calls setGone
         return;
      }

      cluster.executeCommand(new ModEntityCommand(e.entityId, e.status | Core.STATUS_GONE, e.build, e.version), null);
      //         updateStatus(e, e.status | Core.STATUS_GONE);
      e.setSession(null);
      //         if (e.isTetrapod()) {
      //            for (EntityInfo child : entities.values()) {
      //               if (child.parentId == e.entityId) {
      //                  updateStatus(child, child.status | Core.STATUS_GONE);
      //               }
      //            }
      //         }
   }

   public void clearGone(EntityInfo e, Session ses) {
      cluster.executeCommand(new ModEntityCommand(e.entityId, e.status & ~Core.STATUS_GONE, e.build, e.version), null);
      // updateStatus(e, e.status & ~Core.STATUS_GONE);
      e.setSession(ses);
   }

   public int getNumActiveClients() {
      int count = 0;
      for (EntityInfo e : getEntities()) {
         if (e.isClient() && !e.isGone()) {
            count++;
         }
      }
      return count;
   }

   public void onAddEntityCommand(final EntityInfo entity) {
      if (getEntity(entity.entityId) != null && entity.parentId != parentId) {
         entity.queue(new Runnable() {
            public void run() {
               clearAllTopicsAndSubscriptions(entity);
            }
         });
      }
      entities.put(entity.entityId, entity);
      if (entity.isService()) {
         // register their service in our services list
         ensureServicesList(entity.contractId).add(entity);
      }
      if (entity.isService()) {
         broadcaster.broadcastServicesMessage(new ServiceAddedMessage(entity));
      }
   }

   public void onModEntityCommand(final EntityInfo entity) {
      if (entity != null) {
         entity.queue(new Runnable() {
            public void run() {
               if (entity.isService()) {
                  broadcaster.broadcastServicesMessage(new ServiceUpdatedMessage(entity.entityId, entity.status));
               }
            }
         });
      }
   }

   public void onDelEntityCommand(final int entityId) {
      final EntityInfo e = entities.remove(entityId);
      if (e != null) {
         if (e.isService()) {
            e.queue(new Runnable() {
               public void run() {
                  broadcaster.broadcastServicesMessage(new ServiceRemovedMessage(e.entityId));
                  // HACK -- would be 'cleaner' as a listener interface
                  LongPollQueue.clearEntity(e.entityId);
               }
            });
            List<EntityInfo> list = services.get(e.contractId);
            if (list != null) {
               list.remove(e);
            }
         }

         clearAllTopicsAndSubscriptions(e); // might need to do this elsewhere...
      }
   }

}
