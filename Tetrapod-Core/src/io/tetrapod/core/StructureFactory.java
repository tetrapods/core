package io.tetrapod.core;

import io.tetrapod.core.rpc.Structure;

import java.util.*;
import java.util.concurrent.Callable;

public class StructureFactory {

   private final Map<Long, Callable<Structure>> knownStructs = new HashMap<>();
   
   public synchronized void add(int contractId, int structId, Callable<Structure> factory) {
      long key = makeKey(contractId, structId);
      knownStructs.put(key, factory);
   }
   
   // OPTIMIZE: could make this class immutable using a builder pattern and avoid 
   //           this synchronize. adds are rare and usually upfront
   public synchronized Structure make(int serviceId, int structId) {
      long key = makeKey(serviceId, structId);
      Callable<Structure> c = knownStructs.get(key);
      if (c != null) {
         try {
            return c.call();
         } catch (Exception e) {}
      }
      return null;
   }
   
   private final long makeKey(int serviceId, int structId) {
      return ((long)serviceId << 32) | (long)structId;
   }
   
}
