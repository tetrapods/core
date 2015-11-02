package io.tetrapod.core;

import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.tetrapod.core.rpc.Message;
import io.tetrapod.core.rpc.MessageContext;
import io.tetrapod.protocol.core.*;

public class ServiceCache implements TetrapodContract.Services.API {

   private static final Logger                        logger       = LoggerFactory.getLogger(ServiceCache.class);

   /**
    * Maps entityId => Entity
    */
   private final ConcurrentMap<Integer, Entity>       services     = new ConcurrentHashMap<>();

   /**
    * Maps contractId => List of Entities that provide that service
    */
   private final ConcurrentMap<Integer, List<Entity>> serviceLists = new ConcurrentHashMap<>();

   @Override
   public void genericMessage(Message message, MessageContext ctx) {
      assert false;
   }

   @Override
   public void messageServiceAdded(ServiceAddedMessage m, MessageContext ctx) {
      services.put(m.entity.entityId, m.entity);
      getServices(m.entity.contractId).add(m.entity);
   }

   @Override
   public void messageServiceRemoved(ServiceRemovedMessage m, MessageContext ctx) {
      Entity e = services.remove(m.entityId);
      if (e != null) {
         getServices(e.contractId).remove(e);
      }
   }

   @Override
   public void messageServiceUpdated(ServiceUpdatedMessage m, MessageContext ctx) {
      Entity e = services.get(m.entityId);
      if (e != null) {
         synchronized (e) {
            e.status = m.status;
         }
      }
   }

   public List<Entity> getServices(int contractId) {
      List<Entity> list = serviceLists.get(contractId);
      if (list == null) {
         serviceLists.putIfAbsent(contractId, new CopyOnWriteArrayList<Entity>());
         list = serviceLists.get(contractId);
      }
      return list;
   }

   public Entity getRandomAvailableService(int contractId) {
      final List<Entity> list = getServices(contractId);
      if (list != null) {
         final List<Entity> shuffled = new ArrayList<>(list);
         Collections.shuffle(shuffled);
         for (Entity e : shuffled) {
            if (checkAvailable(e)) {
               return e;
            }
         }
      }
      return null;
   }

   public Entity getFirstAvailableService(int contractId) {
      final List<Entity> list = getServices(contractId);
      if (list != null) {
         for (Entity e : list) {
            if (checkAvailable(e)) {
               return e;
            }
         }
      }
      return null;
   }

   public boolean isServiceExistant(int entityId) {
      Entity e = services.get(entityId);
      if (e != null) {
         synchronized (e) {
            return (e.status & Core.STATUS_GONE) == 0;
         }
      }
      return false;
   }

   public boolean isServiceExistant(int entityId, int contractId) {
      Entity e = services.get(entityId);
      if (e != null) {
         synchronized (e) {
            return e.contractId == contractId && (e.status & Core.STATUS_GONE) == 0;
         }
      }
      return false;
   }

   public boolean isServiceAvailable(int entityId) {
      Entity e = services.get(entityId);
      if (e != null) {
         return checkAvailable(e);
      }
      return false;
   }

   public boolean checkDependencies(Set<Integer> contractIds) {
      for (Integer contractId : contractIds) {
         Entity e = getFirstAvailableService(contractId);
         if (e == null) {
            return false;
         }
      }
      return true;
   }

   private boolean checkAvailable(final Entity e) {
      synchronized (e) {
         return isAvailable(e.status);
      }
   }

   public Entity getEntity(int entityId) {
      return services.get(entityId);
   }

   public static final boolean isAvailable(final int status) {
      return (status & (Core.STATUS_STARTING | Core.STATUS_PAUSED | Core.STATUS_GONE | Core.STATUS_BUSY | Core.STATUS_OVERLOADED
               | Core.STATUS_FAILED | Core.STATUS_STOPPING | Core.STATUS_PASSIVE)) == 0;
   }
}
