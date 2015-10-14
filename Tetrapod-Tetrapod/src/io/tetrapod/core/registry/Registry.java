package io.tetrapod.core.registry;

import static io.tetrapod.protocol.core.MessageHeader.TO_ENTITY;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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

   protected static final Logger                logger          = LoggerFactory.getLogger(Registry.class);

   public static final int                      MAX_PARENTS     = 0x000007FF;
   public static final int                      MAX_ID          = 0x000FFFFF;

   public static final int                      PARENT_ID_SHIFT = 20;
   public static final int                      PARENT_ID_MASK  = MAX_PARENTS << PARENT_ID_SHIFT;
   public static final int                      BOOTSTRAP_ID    = 1 << PARENT_ID_SHIFT;

   /**
    * A read-write lock is used to synchronize subscriptions to the registry state, and it is a little counter-intuitive. When making write
    * operations to the registry, we grab the read lock to allow concurrent writes across the registry. When we need to send the current
    * state snapshot to another cluster member, we grab the write lock for exclusive access to send a consistent state.
    */
   public final ReadWriteLock                   lock            = new ReentrantReadWriteLock();

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
      //return cluster.getEntities();
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
      //return cluster.getEntity(entityId);
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
         int id = parentId | (++counter % MAX_ID);
         if (getEntity(id) == null) {
            return id;
         }
      }
   }

   private void clearAllTopicsAndSubscriptions(final EntityInfo e) {
      logger.debug("clearAllTopicsAndSubscriptions: {}", e);
      // Unpublish all their topics
      for (Topic topic : e.getTopics()) {
         unpublish(e, topic.topicId);
      }
      // Unsubscribe from all subscriptions
      for (Topic topic : e.getSubscriptions()) {
         EntityInfo owner = getEntity(topic.ownerId);
         // assert (owner != null); 
         if (owner != null) {
            unsubscribe(owner, topic.topicId, e.entityId, true);
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

   public Topic publish(int entityId) {
      lock.readLock().lock();
      try {
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
      } finally {
         lock.readLock().unlock();
      }
      return null;
   }

   public boolean unpublish(EntityInfo e, int topicId) {
      lock.readLock().lock();
      try {
         final Topic topic = e.unpublish(topicId);
         if (topic != null) {
            // clean up all the subscriptions to this topic
            synchronized (topic) {
               for (Subscriber sub : topic.getSubscribers().toArray(new Subscriber[0])) {
                  unsubscribe(e, topic, sub.entityId, true);
               }
            }
            if (e.parentId == parentId) {
               broadcaster.broadcastRegistryMessage(new TopicUnpublishedMessage(e.entityId, topicId));
            }
            return true;
         } else {
            logger.info("Could not find topic {} for entity {}", topicId, e);
         }
      } finally {
         lock.readLock().unlock();
      }
      return false;
   }

   public void subscribe(final EntityInfo publisher, final int topicId, final int entityId, final boolean once) {
      lock.readLock().lock();
      try {
         final Topic topic = publisher.getTopic(topicId);
         if (topic != null) {
            final EntityInfo e = getEntity(entityId);
            if (e != null) {
               topic.subscribe(publisher, e, parentId, once);
               e.subscribe(topic);

               if (publisher.parentId == parentId) {
                  broadcaster.broadcastRegistryMessage(new TopicSubscribedMessage(topic.ownerId, topic.topicId, e.entityId, once));
               }
            } else {
               logger.info("Could not find subscriber {} for topic {}", entityId, topicId);
            }
         } else {
            logger.info("Could not find topic {} for {}", topicId, publisher);
         }
      } finally {
         lock.readLock().unlock();
      }
   }

   public void unsubscribe(final EntityInfo publisher, final int topicId, final int entityId, final boolean all) {
      assert (publisher != null);
      lock.readLock().lock();
      try {
         final Topic topic = publisher.getTopic(topicId);
         if (topic != null) {
            unsubscribe(publisher, topic, entityId, all);
         } else {
            logger.info("Could not find topic {} for {}", topicId, publisher);
         }
      } finally {
         lock.readLock().unlock();
      }
   }

   public void unsubscribe(final EntityInfo publisher, Topic topic, final int entityId, final boolean all) {
      assert (publisher != null);
      assert (topic != null);
      lock.readLock().lock();
      try {
         final EntityInfo e = getEntity(entityId);
         if (e != null) {
            final boolean isProxy = !e.isTetrapod() && e.parentId != parentId;
            if (topic.unsubscribe(e.entityId, e.parentId, isProxy, all)) {
               e.unsubscribe(topic);
            }
            if (publisher.parentId == parentId) {
               broadcaster.broadcastRegistryMessage(new TopicUnsubscribedMessage(publisher.entityId, topic.topicId, entityId));
            }
         } else {
            logger.info("Could not find subscriber {} for topic {}", entityId, topic.topicId);
         }
      } finally {
         lock.readLock().unlock();
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public void genericMessage(Message message, MessageContext ctx) {}

   @Override
   public void messageEntityRegistered(EntityRegisteredMessage m, MessageContext ctx) {
      //      // TODO: validate sender    
      //      if (ctx.header.fromId != parentId) {
      //         lock.readLock().lock();
      //         try {
      //            EntityInfo info = entities.get(m.entity.entityId);
      //            if (info != null) {
      //               info.parentId = m.entity.parentId;
      //               info.reclaimToken = m.entity.reclaimToken;
      //               info.host = m.entity.host;
      //               info.status = m.entity.status;
      //               info.build = m.entity.build;
      //               info.name = m.entity.name;
      //               info.version = m.entity.version;
      //               info.contractId = m.entity.contractId;
      //               final EntityInfo e = info;
      //               info.queue(new Runnable() {
      //                  public void run() {
      //                     clearAllTopicsAndSubscriptions(e);
      //                  }
      //               });
      //            } else {
      //               info = new EntityInfo(m.entity);
      //            }
      //            register(info);
      //         } finally {
      //            lock.readLock().unlock();
      //         }
      //      }
   }

   @Override
   public void messageEntityUnregistered(EntityUnregisteredMessage m, MessageContext ctx) {
      // TODO: validate sender           
      //      if (ctx.header.fromId != parentId) {
      //         final EntityInfo e = getEntity(m.entityId);
      //         if (e != null) {
      //            e.queue(new Runnable() {
      //               public void run() {
      //                  unregister(e);
      //               }
      //            });
      //         } else {
      //            logger.error("Could not find entity {} to unregister", m.entityId);
      //         }
      //      }
   }

   @Override
   public void messageEntityUpdated(final EntityUpdatedMessage m, MessageContext ctx) {
      // TODO: validate sender           
      //      if (ctx.header.fromId != parentId) {
      //         final EntityInfo e = getEntity(m.entityId);
      //         if (e != null) {
      //            e.queue(new Runnable() {
      //               public void run() {
      //                  updateStatus(e, m.status);
      //               }
      //            });
      //         } else {
      //            logger.error("Could not find entity {} to update", m.entityId);
      //         }
      //      }
   }

   @Override
   public void messageTopicPublished(final TopicPublishedMessage m, MessageContext ctx) {
      // TODO: validate sender
      if (ctx.header.fromId != parentId) {
         final EntityInfo owner = getEntity(m.ownerId);
         if (owner != null) {
            owner.queue(new Runnable() {
               public void run() {
                  owner.nextTopicId();// increment our topic counter
                  owner.publish(m.topicId);
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
                  unpublish(owner, m.topicId);
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
                  subscribe(owner, m.topicId, m.entityId, m.once);
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

   public void sendRegistryState(final Session session, final int toEntityId, final int topicId) {
      lock.writeLock().lock();
      try {
         // Sends all current entities -- ourselves, and our children
         final EntityInfo me = getEntity(parentId);
         session.sendMessage(new EntityRegisteredMessage(me), TO_ENTITY, toEntityId);
         for (Topic t : me.getTopics()) {
            session.sendMessage(new TopicPublishedMessage(me.entityId, t.topicId), TO_ENTITY, toEntityId);
         }

         for (EntityInfo e : getChildren()) {
            session.sendMessage(new EntityRegisteredMessage(e), TO_ENTITY, toEntityId);
            for (Topic t : e.getTopics()) {
               session.sendMessage(new TopicPublishedMessage(e.entityId, t.topicId), TO_ENTITY, toEntityId);
            }
         }
         // send topic info
         // OPTIMIZE: could be optimized greatly with custom messages, but this is very simple
         sendSubscribers(me, session, toEntityId, topicId);
         for (EntityInfo e : getChildren()) {
            sendSubscribers(e, session, toEntityId, topicId);
         }
         session.sendMessage(new EntityListCompleteMessage(), TO_ENTITY, toEntityId);
      } finally {
         lock.writeLock().unlock();
      }
   }

   private void sendSubscribers(final EntityInfo e, final Session session, final int toEntityId, final int topicId) {
      for (Topic t : e.getTopics()) {
         for (Subscriber s : t.getSubscribers()) {
            session.sendMessage(new TopicSubscribedMessage(t.ownerId, t.topicId, s.entityId, false), TO_ENTITY, toEntityId);
         }
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
      lock.readLock().lock();
      try {
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
      } finally {
         lock.readLock().unlock();
      }
   }

   public void onModEntityCommand(final EntityInfo entity) {
      if (entity != null) {
         entity.queue(new Runnable() {
            public void run() {
               lock.readLock().lock();
               try {
                  if (entity.isService()) {
                     broadcaster.broadcastServicesMessage(new ServiceUpdatedMessage(entity.entityId, entity.status));
                  }
               } finally {
                  lock.readLock().unlock();
               }
            }
         });
      }
   }

   public void onDelEntityCommand(final int entityId) {
      final EntityInfo e = entities.remove(entityId);
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
