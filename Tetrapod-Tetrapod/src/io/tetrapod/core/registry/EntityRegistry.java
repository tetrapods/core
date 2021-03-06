package io.tetrapod.core.registry;

import java.util.*;
import java.util.concurrent.*;

import org.slf4j.*;

import io.tetrapod.core.rpc.Message;
import io.tetrapod.core.storage.*;
import io.tetrapod.protocol.core.*;

/**
 * The global registry of all current actors in the system and their published topics and subscriptions
 */
public class EntityRegistry {

   protected static final Logger                logger   = LoggerFactory.getLogger(EntityRegistry.class);

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
    * A list of recently deleted entities we need to clean up
    */
   private final Queue<EntityInfo>              deleted  = new ConcurrentLinkedQueue<>();

   /**
    * Maps contractId => List of EntityInfos that provide that service
    */
   private final Map<Integer, List<EntityInfo>> services = new ConcurrentHashMap<>();

   public static interface RegistryBroadcaster {
      public void broadcastServicesMessage(Message msg);

      public void sendMessage(Message msg, int toEntityId, int toChildId);
   }

   private final RegistryBroadcaster broadcaster;
   private final TetrapodCluster     cluster;

   public EntityRegistry(RegistryBroadcaster broadcaster, TetrapodCluster cluster) {
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

   public Set<EntityInfo> getChildren() {
      final Set<EntityInfo> children = new HashSet<>();
      for (EntityInfo e : getEntities()) {
         if (e.parentId == parentId && e.entityId != parentId) {
            children.add(e);
         }
      }
      for (EntityInfo e : cluster.getEntities()) {
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
            if (info != null && info.isAvailable() && info.getSession() != null) {
               return info;
            }
         }
      }
      logger.warn("Could not find a random available service for contractId={} in list of {} services", contractId,
            list == null ? "null" : list.size());
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

   public void unregister(final EntityInfo e) {
      if (e.isTetrapod() && cluster.isValidPeer(e.entityId)) {
         logger.warn("Setting {} as GONE (unregister context)", e); // TEMP DEBUG LOGGING
         setGone(e);
      } else {
         cluster.executeCommand(new DelEntityCommand(e.entityId), entry -> {});
      }
   }

   public void updateStatus(EntityInfo e, int status, int mask) {
      cluster.executeCommand(new ModEntityCommand(e.entityId, status, mask, e.build, e.version), null);
   }

   //////////////////////////////////////////////////////////////////////////////////////////

   public synchronized void logStats(boolean includeClients) {
      List<EntityInfo> list = new ArrayList<>(getEntities());
      Collections.sort(list);
      logger.info("======================================= TETRAPOD CLUSTER REGISTRY =========================================");
      for (EntityInfo e : list) {
         if (includeClients || !e.isClient())
            logger.info(String.format(" 0x%08X 0x%08X %-15s status=%08X [%s]", e.parentId, e.entityId, e.name, e.status,
                  e.hasConnectedSession() ? "CONNECTED" : "*"));
      }
   }

   public List<EntityInfo> getServicesList(int contractId) {
      List<EntityInfo> list = services.get(contractId);
      if (list == null) {
         list = new CopyOnWriteArrayList<>();
         services.put(contractId, list);
      }
      return list;
   }

   public void setGone(EntityInfo e) {
      logger.info("Setting {} as GONE", e);

      updateStatus(e, Core.STATUS_GONE, Core.STATUS_GONE);
   }

   public void clearGone(EntityInfo e) {
      logger.info("Setting {} as BACK", e);
      updateStatus(e, 0, Core.STATUS_GONE);
   }

   public void onAddEntityCommand(final EntityInfo entity) {
      // if we had a temporary EntityInfo stored in registry while waiting for the command to commit, we can now move 
      // the stored session object into the new official entity object produced by this command
      final EntityInfo old = entities.get(entity.entityId);
      if (old != null && old.reclaimToken == entity.reclaimToken) {
         entity.setSession(old.session);
      }

      entities.put(entity.entityId, entity);
      if (entity.isService()) {
         // register their service in our services list
         List<EntityInfo> list = getServicesList(entity.contractId);
         for (EntityInfo e : new ArrayList<>(list)) {
            if (e.entityId == entity.entityId) {
               list.remove(e);
            }
         }
         list.add(entity);
         entity.queue(() -> broadcaster.broadcastServicesMessage(new ServiceAddedMessage(entity)));
      }
   }

   public void onModEntityCommand(final EntityInfo entity) {
      if (entity != null && entity.isService()) {
         entity.queue(() -> {
            broadcaster.broadcastServicesMessage(new ServiceUpdatedMessage(entity.entityId, entity.status));
         });
      }
   }

   public void onDelEntityCommand(final int entityId) {
      final EntityInfo e = entities.get(entityId);
      if (e != null) {
         e.queue(() -> {
            entities.remove(entityId);
            if (e.isService()) {
               broadcaster.broadcastServicesMessage(new ServiceRemovedMessage(e.entityId));
               final List<EntityInfo> list = services.get(e.contractId);
               if (list != null) {
                  list.remove(e);
               }
            }
         });
         // add to list so we can drain queue later
         deleted.add(e);
      } else {
         logger.error("onDelEntityCommand Couldn't find {}", entityId);
      }
   }

   public Queue<EntityInfo> getDeletedEntities() {
      return deleted;
   }

}
