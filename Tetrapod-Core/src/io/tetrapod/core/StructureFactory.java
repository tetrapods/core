package io.tetrapod.core;

import io.tetrapod.core.rpc.Structure;

import java.util.*;
import java.util.concurrent.Callable;

public class StructureFactory {

   private final Map<Integer, Callable<Structure>> knownStructs = new HashMap<>();
   
   public synchronized void add(int dynamicId, int structId, Callable<Structure> factory) {
      int key = makeKey(dynamicId, structId);
      knownStructs.put(key, factory);
   }
   
   // OPTIMIZE: could make this class immutable using a builder pattern and avoid 
   //           this synchronize. adds are rare and usually upfront
   public synchronized Structure make(int dynamicId, int structId) {
      int key = makeKey(dynamicId, structId);
      Callable<Structure> c = knownStructs.get(key);
      if (c != null) {
         try {
            return c.call();
         } catch (Exception e) {}
      }
      return null;
   }
   
   private final int makeKey(int dynamicId, int structId) {
      return (dynamicId << 20) | structId;
   }
}
