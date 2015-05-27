package io.tetrapod.core.storage;

import io.tetrapod.core.StructureFactory;
import io.tetrapod.core.serialize.StructureAdapter;
import io.tetrapod.core.serialize.datasources.TempBufferDataSource;
import io.tetrapod.core.web.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.raft.*;
import io.tetrapod.raft.storage.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Tetrapod state machine adds cluster properties, service protocols, and tetrapod web routes
 */
public class TetrapodStateMachine extends StorageStateMachine<TetrapodStateMachine> {

   private static final String                    TETRAPOD_PREF_PREFIX            = "tetrapod.prefs::";
   private static final String                    TETRAPOD_CONTRACT_PREFIX        = "tetrapod.contract::";
   private static final String                    TETRAPOD_WEBROOT_PREFIX         = "tetrapod.webroot::";

   public static final int                        SET_CLUSTER_PROPERTY_COMMAND_ID = 400;
   public static final int                        DEL_CLUSTER_PROPERTY_COMMAND_ID = 401;
   public static final int                        REGISTER_CONTRACT_COMMAND_ID    = 402;
   public static final int                        SET_WEB_ROUTE_COMMAND_ID        = 403;
   public static final int                        DEL_WEB_ROUTE_COMMAND_ID        = 404;

   public final Map<String, ClusterProperty>      props                           = new HashMap<>();
   public final Map<Integer, ContractDescription> contracts                       = new HashMap<>();
   public final Map<String, WebRootDef>           webRootDefs                     = new HashMap<>();

   public final WebRoutes                         webRoutes                       = new WebRoutes();
   public final ConcurrentMap<String, WebRoot>    webRootDirs                     = new ConcurrentHashMap<>();

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
      registerCommand(DelClusterPropertyCommand.COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new DelClusterPropertyCommand();
         }
      });
      registerCommand(RegisterContractCommand.COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new RegisterContractCommand();
         }
      });
      registerCommand(SetWebRouteCommand.COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new SetWebRouteCommand();
         }
      });
      registerCommand(DelWebRouteCommand.COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new DelWebRouteCommand();
         }
      });
   }

   @Override
   public void saveState(DataOutputStream out) throws IOException {
      super.saveState(out);
   }

   @Override
   public void loadState(DataInputStream in) throws IOException {
      super.loadState(in);

      // iterate over the storage items and extract objects (properties, web roots, contracts)
      for (StorageItem item : items.values()) {
         if (item.key.startsWith(TETRAPOD_PREF_PREFIX)) {
            ClusterProperty prop = new ClusterProperty();
            prop.read(TempBufferDataSource.forReading(item.getData()));
            setProperty(prop, false);
         } else if (item.key.startsWith(TETRAPOD_CONTRACT_PREFIX)) {
            ContractDescription info = new ContractDescription();
            info.read(TempBufferDataSource.forReading(item.getData()));
            registerContract(info, false);
         } else if (item.key.startsWith(TETRAPOD_WEBROOT_PREFIX)) {
            WebRootDef info = new WebRootDef();
            info.read(TempBufferDataSource.forReading(item.getData()));
            setWebRoot(info, false);
         }
      }
   }

   public void registerContract(ContractDescription info, boolean write) {
      if (write) {
         // Write as storage item      
         putItem(TETRAPOD_CONTRACT_PREFIX + info.contractId, (byte[]) info.toRawForm(TempBufferDataSource.forWriting()));
      }
      logger.info(" ContractDescription = {}", info.dump());
      // reg the structs
      if (info.structs != null) {
         for (StructDescription sd : info.structs) {
            StructureFactory.add(new StructureAdapter(sd));
         }
      }
      // reg the web routes
      if (info.routes != null) {
         for (WebRoute r : info.routes) {
            webRoutes.setRoute(r.path, r.contractId, r.structId);
            logger.debug("Setting Web route [{}] for {}", r.path, r.contractId);
         }
      }
      // keep local cache of contracts
      contracts.put(info.contractId, info);
   }

   public boolean hasContract(int contractId, int version) {
      ContractDescription info = contracts.get(contractId);
      if (info != null) {
         return info.version >= version;
      }
      return false;
   }

   public void setProperty(ClusterProperty prop, boolean write) {
      if (write) {
         // store in state machine as a StorageItem
         putItem(TETRAPOD_PREF_PREFIX + prop.key, (byte[]) prop.toRawForm(TempBufferDataSource.forWriting()));
      }
      // keep local caches
      props.put(prop.key, prop);
      System.setProperty(prop.key, prop.val);
   }

   public void delProperty(String key) {
      // remove from backing store
      removeItem(TETRAPOD_PREF_PREFIX + key);
      // remove from local caches
      props.remove(key);
      System.clearProperty(key);
   }

   public void setWebRoot(WebRootDef def, boolean write) {
      logger.info(" Loaded WebRootDef = {}", def.dump());
      if (write) {
         // store in state machine as a StorageItem
         putItem(TETRAPOD_WEBROOT_PREFIX + def.name, (byte[]) def.toRawForm(TempBufferDataSource.forWriting()));
      }
      webRootDefs.put(def.name, def);
      if (def.file != null) {
         WebRoot wr = new WebRootLocalFilesystem(def.path, new File(def.file));
         webRootDirs.put(def.name, wr);
      }
   }

   public void delWebRoot(String name) {
      // remove from backing store
      removeItem(TETRAPOD_WEBROOT_PREFIX + name);
      // remove from local caches
      webRootDefs.remove(name);
      webRootDirs.remove(name);
   }

}
