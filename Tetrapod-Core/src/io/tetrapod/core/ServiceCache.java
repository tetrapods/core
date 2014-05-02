package io.tetrapod.core;

import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceCache implements TetrapodContract.Services.API {

   /**
    * Maps entityId => Entity
    */
   private final Map<Integer, Entity>       services     = new ConcurrentHashMap<>();

   /**
    * Maps contractId => List of Entities that provide that service
    */
   private final Map<Integer, List<Entity>> serviceLists = new ConcurrentHashMap<>();

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
         e.status = m.status;
      }
   }

   public synchronized List<Entity> getServices(int contractId) {
      List<Entity> list = serviceLists.get(contractId);
      if (list == null) {
         list = new ArrayList<>();
         serviceLists.put(contractId, list);
      }
      return list;
   }

   public Entity getRandomAvailableService(int contractId) {
      final List<Entity> list = getServices(contractId);
      if (list != null) {
         final List<Entity> shuffled = new ArrayList<>(list);
         Collections.shuffle(shuffled);
         for (Entity e : shuffled) {
            if (isAvailable(e)) {
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
            if (isAvailable(e)) {
               return e;
            }
         }
      }
      return null;
   }

   public synchronized boolean checkDependencies(Set<Integer> contractIds) {
      for (Integer contractId : contractIds) {
         Entity e = getFirstAvailableService(contractId);
         if (e == null) {
            return false;
         }
      }
      return true;
   }
   
   /**
    * Copied form EntityInfo which is unfortunate.
    */
   public static final boolean isAvailable(final Entity e) {
      return (e.status & (Core.STATUS_STARTING | Core.STATUS_PAUSED | Core.STATUS_GONE | Core.STATUS_BUSY | Core.STATUS_OVERLOADED
            | Core.STATUS_FAILED | Core.STATUS_STOPPING)) == 0;
   }
}
