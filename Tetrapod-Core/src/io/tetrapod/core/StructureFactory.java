package io.tetrapod.core;

import io.tetrapod.core.rpc.Structure;

import java.util.*;
import java.util.concurrent.Callable;

public class StructureFactory {

   private final Map<Integer, Callable<Structure>> knownStructs = new HashMap<>();
   
   public synchronized void add(int structId, int apiId, Callable<Structure> factory) {
      int key = makeKey(structId, apiId);
      knownStructs.put(key, factory);
   }
   
   // OPTIMIZE: could make this class immutable using a builder pattern and avoid 
   //           this synchronize. adds are rare and usually upfront
   public synchronized Structure makeStructure(int structId, int apiId) {
      int key = makeKey(structId, apiId);
      Callable<Structure> c = knownStructs.get(key);
      if (c != null) {
         try {
            return c.call();
         } catch (Exception e) {}
      }
      return null;
   }
   
   private final int makeKey(int structId, int apiId) {
      return (apiId << 20) & structId;
   }
}
