package io.tetrapod.core.storage;

import io.tetrapod.protocol.core.ClusterProperty;
import io.tetrapod.raft.*;
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
      registerCommand(SetClusterPropertyCommand.COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new SetClusterPropertyCommand();
         }
      });

      for (ClusterProperty prop : props.values()) {
         System.setProperty(prop.key, prop.val);
      }

   }

   public void setProperty(ClusterProperty prop) {
      props.put(prop.key, prop);
      System.setProperty(prop.key, prop.val);
   }

   public void delProperty(String key) {
      props.remove(key);
      System.clearProperty(key);
   }

}
