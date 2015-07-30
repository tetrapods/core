package io.tetrapod.core.storage;

import io.tetrapod.core.StructureFactory;
import io.tetrapod.core.serialize.StructureAdapter;
import io.tetrapod.core.serialize.datasources.TempBufferDataSource;
import io.tetrapod.core.utils.*;
import io.tetrapod.core.web.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.raft.StateMachine;
import io.tetrapod.raft.storage.*;

import java.io.*;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

/**
 * Tetrapod state machine adds cluster properties, service protocols, and tetrapod web routes
 */
public class TetrapodStateMachine extends StorageStateMachine<TetrapodStateMachine> {

   public final static int                        TETRAPOD_STATE_FILE_VERSION     = 1;

   private static final String                    TETRAPOD_PREF_PREFIX            = "tetrapod.prefs::";
   private static final String                    TETRAPOD_CONTRACT_PREFIX        = "tetrapod.contract::";
   private static final String                    TETRAPOD_WEBROOT_PREFIX         = "tetrapod.webroot::";
   private static final String                    TETRAPOD_ADMIN_PREFIX           = "tetrapod.admin::";
   private static final String                    TETRAPOD_ADMIN_ACCOUNT_SEQ_KEY  = "tetrapod.admin.account.seq";
   private static final String                    TETRAPOD_OWNER_PREFIX           = "tetrapod.owner::";

   public static final int                        SET_CLUSTER_PROPERTY_COMMAND_ID = 400;
   public static final int                        DEL_CLUSTER_PROPERTY_COMMAND_ID = 401;
   public static final int                        REGISTER_CONTRACT_COMMAND_ID    = 402;
   public static final int                        SET_WEB_ROUTE_COMMAND_ID        = 403;
   public static final int                        DEL_WEB_ROUTE_COMMAND_ID        = 404;
   public static final int                        ADD_ADMIN_COMMAND_ID            = 405;
   public static final int                        DEL_ADMIN_COMMAND_ID            = 406;
   public static final int                        MOD_ADMIN_COMMAND_ID            = 407;
   public static final int                        CLAIM_OWNERSHIP_COMMAND_ID      = 408;
   public static final int                        RETAIN_OWNERSHIP_COMMAND_ID     = 409;
   public static final int                        RELEASE_OWNERSHIP_COMMAND_ID    = 410;

   public final Map<String, ClusterProperty>      props                           = new HashMap<>();
   public final Map<Integer, ContractDescription> contracts                       = new HashMap<>();
   public final Map<String, WebRootDef>           webRootDefs                     = new HashMap<>();
   public final Map<Integer, Admin>               admins                          = new HashMap<>();
   public final WebRoutes                         webRoutes                       = new WebRoutes();
   public final Map<String, WebRoot>              webRootDirs                     = new ConcurrentHashMap<>();

   public final Map<Integer, Owner>               owners                          = new ConcurrentHashMap<>();
   public final Map<String, Owner>                ownedItems                      = new ConcurrentHashMap<>();

   private final Executor                         webRootSequentialExecutor       = new ThreadPoolExecutor(0, 1, 5L, TimeUnit.SECONDS,
                                                                                        new LinkedBlockingQueue<Runnable>());

   protected SecretKey                            secretKey;

   public static class Factory implements StateMachine.Factory<TetrapodStateMachine> {
      public TetrapodStateMachine makeStateMachine() {
         return new TetrapodStateMachine();
      }
   }

   public TetrapodStateMachine() {
      super();
      SetClusterPropertyCommand.register(this);
      DelClusterPropertyCommand.register(this);
      RegisterContractCommand.register(this);
      SetWebRouteCommand.register(this);
      DelWebRouteCommand.register(this);
      AddAdminUserCommand.register(this);
      DelAdminUserCommand.register(this);
      ModAdminUserCommand.register(this);
      ClaimOwnershipCommand.register(this);
      RetainOwnershipCommand.register(this);
      ReleaseOwnershipCommand.register(this);
      initSecretKey();
   }

   @Override
   public void saveState(DataOutputStream out) throws IOException {
      out.writeInt(TETRAPOD_STATE_FILE_VERSION);
      super.saveState(out);
   }

   private void initSecretKey() {
      try {
         final char[] keyPassword = Util.getProperty("raft.tetrapod.key", "??!!deefault!!??").toCharArray();
         final byte[] keySalt = Util.getProperty("raft.tetrapod.salt", "??!!deesault!!??").getBytes("UTF-8");
         secretKey = AESEncryptor.makeKey(keyPassword, keySalt);
      } catch (NoSuchAlgorithmException | InvalidKeySpecException | UnsupportedEncodingException e) {
         Fail.fail(e);
      }
   }

   @Override
   public void loadState(DataInputStream in) throws IOException {
      props.clear();
      contracts.clear();
      webRootDefs.clear();
      webRoutes.clear();
      webRootDirs.clear();
      admins.clear();
      owners.clear();

      final int fileVersion = in.readInt();
      if (fileVersion > TETRAPOD_STATE_FILE_VERSION) {
         throw new IOException("Incompatible Snapshot Format: " + fileVersion + " > " + TETRAPOD_STATE_FILE_VERSION);
      }

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
         } else if (item.key.startsWith(TETRAPOD_ADMIN_PREFIX)) {
            Admin admin = new Admin();
            admin.read(TempBufferDataSource.forReading(item.getData()));
            addAdminUser(admin, false);
         } else if (item.key.startsWith(TETRAPOD_OWNER_PREFIX)) {
            Owner owner = new Owner();
            owner.read(TempBufferDataSource.forReading(item.getData()));
            saveOwner(owner, false);
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
         return info.version > version;
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
      try {
         System.setProperty(prop.key, AESEncryptor.decryptSaltedAES(prop.val, secretKey));
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
      }
   }

   public void delProperty(String key) {
      // remove from backing store
      removeItem(TETRAPOD_PREF_PREFIX + key);
      // remove from local caches
      props.remove(key);
      System.clearProperty(key);
   }

   public void setWebRoot(final WebRootDef def, boolean write) {
      logger.info(" Loaded WebRootDef = {}", def.dump());
      if (write) {
         // store in state machine as a StorageItem
         putItem(TETRAPOD_WEBROOT_PREFIX + def.name, (byte[]) def.toRawForm(TempBufferDataSource.forWriting()));
      }
      // add to local cache, in thread as it could take a while for downloading files
      webRootSequentialExecutor.execute(new Runnable() {
         public void run() {
            try {
               if (!Util.isEmpty(def.file) && !Util.isEmpty(def.path)) {
                  WebRoot wr = null;
                  if (def.file.startsWith("http")) {
                     wr = new WebRootLocalFilesystem(def.path, new URL(def.file));
                  } else {
                     wr = new WebRootLocalFilesystem(def.path, new File(def.file));
                  }
                  webRootDefs.put(def.name, def);
                  webRootDirs.put(def.name, wr);
               }
            } catch (IOException e) {
               logger.error(e.getMessage(), e);
            }
         }
      });
   }

   public void delWebRoot(final String name) {
      logger.info(" Deleting WebRootDef = {}", name);
      // remove from backing store
      removeItem(TETRAPOD_WEBROOT_PREFIX + name);
      // remove from local caches, needs to happen in sequence with add
      webRootSequentialExecutor.execute(new Runnable() {
         public void run() {
            webRootDefs.remove(name);
            webRootDirs.remove(name);
         }
      });
   }

   public void addAdminUser(final Admin user, boolean write) {
      if (write) {
         for (Admin admin : admins.values()) {
            if (admin.email.equalsIgnoreCase(user.email))
               return; // don't clobber existing
         }

         user.accountId = (int) increment(TETRAPOD_ADMIN_ACCOUNT_SEQ_KEY, 1);

         // store in state machine as a StorageItem
         putItem(TETRAPOD_ADMIN_PREFIX + user.accountId, (byte[]) user.toRawForm(TempBufferDataSource.forWriting()));
      }
      admins.put(user.accountId, user);
      logger.info(" Adding Admin = {}", user.dump());
   }

   public void modifyAdminUser(final Admin user) {
      Admin orig = admins.get(user.accountId);
      if (orig != null) {
         // store in state machine as a StorageItem
         putItem(TETRAPOD_ADMIN_PREFIX + user.accountId, (byte[]) user.toRawForm(TempBufferDataSource.forWriting()));
         admins.put(user.accountId, user);
      } else {
         throw new RuntimeException("Admin user not found " + user.accountId);
      }
   }

   public void delAdminUser(int accountId) {
      logger.info(" Deleting Admin = {}", accountId);
      // remove from backing store
      removeItem(TETRAPOD_ADMIN_PREFIX + accountId);
      // remove from local caches
      admins.remove(accountId);
   }

   public void saveOwner(Owner owner, boolean write) {
      if (write) {
         // store in state machine as a StorageItem         
         putItem(TETRAPOD_OWNER_PREFIX + owner.entityId, (byte[]) owner.toRawForm(TempBufferDataSource.forWriting()));
      }
      // keep local caches
      owners.put(owner.entityId, owner);
      for (String key : owner.keys) {
         ownedItems.put(key, owner);
      }
   }

   public boolean claimOwnership(int ownerId, long leaseMillis, String key, long curTime) {
      logger.info("CLAIM OWNERSHIP COMMAND: {} {} {}", ownerId, leaseMillis, key, curTime);

      // see if there is a current owner
      final Owner owner = ownedItems.get(key);
      if (owner != null) {
         if (owner.expiry > curTime) {
            logger.warn("Already owned by {}", owner.dump());
            return false;
         } else {
            releaseOwnership(owner, null);
         }
      }

      Owner me = owners.get(ownerId);
      if (me == null) {
         me = new Owner(ownerId, leaseMillis, new ArrayList<String>());
      }
      me.expiry = Math.max(me.expiry, leaseMillis + curTime);
      me.keys.add(key);
      saveOwner(me, true);
      logger.info("**** {} CLAIMED BY {}", key, me.dump());
      return true;
   }

   public void retainOwnership(int ownerId, int leaseMillis, long curTime) {
      logger.info("RETAIN OWNERSHIP COMMAND: {} {} {}", ownerId, leaseMillis, curTime);
      final Owner me = owners.get(ownerId);
      if (me != null) {
         me.expiry = Math.max(me.expiry, leaseMillis + curTime);
         saveOwner(me, true);
      }
   }

   public void releaseOwnership(int ownerId, String[] keys) {
      logger.info("RELEASE OWNERSHIP COMMAND: {} {}", ownerId, keys);
      final Owner owner = owners.get(ownerId);
      if (owner != null) {
         releaseOwnership(owner, keys);
      }
   }

   private void releaseOwnership(final Owner owner, String[] keys) {
      if (keys == null) {
         for (String k : owner.keys) {
            if (ownedItems.get(k) == owner) {
               ownedItems.remove(k);
            }
         }
         removeItem(TETRAPOD_OWNER_PREFIX + owner.entityId);
         owners.remove(owner.entityId);
      } else {
         for (String k : keys) {
            if (ownedItems.get(k) == owner) {
               ownedItems.remove(k);
            }
            owner.keys.remove(k);
         }
         saveOwner(owner, true);
      }
   }
}
