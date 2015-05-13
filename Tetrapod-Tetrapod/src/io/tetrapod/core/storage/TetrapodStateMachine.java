package io.tetrapod.core.storage;

import io.tetrapod.protocol.core.ClusterProperty;
import io.tetrapod.raft.StateMachine;
import io.tetrapod.raft.storage.StorageStateMachine;

import java.util.*;

/**
 * Tetrapod state machine adds cluster properties, service protocols, and tetrapod web routes
 */
public class TetrapodStateMachine extends StorageStateMachine<TetrapodStateMachine> {

   public final Map<String, ClusterProperty> props = new HashMap<>();

   // public final Map<Integer, Object>           protocols   = new HashMap<>(); // StructureFactory stuff
   // public final WebRoutes                      webRoutes   = new WebRoutes();
   // public final ConcurrentMap<String, WebRoot> webRootDirs = new ConcurrentHashMap<>();

   public static class Factory implements StateMachine.Factory<TetrapodStateMachine> {
      public TetrapodStateMachine makeStateMachine() {
         return new TetrapodStateMachine();
      }
   }

   public TetrapodStateMachine() {
      super();
   }

}
