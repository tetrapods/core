package io.tetrapod.core.storage;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.tetrapod.core.utils.Util;
import io.tetrapod.core.utils.Value;
import io.tetrapod.raft.storage.LockCommand;
import io.tetrapod.raft.storage.UnlockCommand;

/**
 * Convenience wrapper for a Distributed Lock via raft
 */
public class DistributedLock {

   private static final Logger logger = LoggerFactory.getLogger(DistributedLock.class);

   final private TetrapodCluster raft;

   final public String key;
   final public String uuid;

   public DistributedLock(String key, TetrapodCluster raft) {
      this(key, UUID.randomUUID().toString(), raft);
   }

   public DistributedLock(String key, String uuid, TetrapodCluster raft) {
      this.key = key;
      this.uuid = uuid;
      this.raft = raft;
   }

   public boolean lock(long leaseForMillis, long waitForMillis) {
      final long started = System.currentTimeMillis();
      logger.info("LOCKING {} ...", key);
      final Value<Boolean> acquired = new Value<>(false);
      int attempts = 0;
      while (!acquired.get() && started + waitForMillis > System.currentTimeMillis()) {
         if (attempts++ > 0) {
            Util.sleep(Math.min(1024, 8 * attempts * attempts));
         }
         raft.executeCommand(new LockCommand<TetrapodStateMachine>(key, uuid, leaseForMillis, System.currentTimeMillis()),
                  e -> {
                     if (e != null) {
                        final LockCommand<TetrapodStateMachine> command = (LockCommand<TetrapodStateMachine>) e.getCommand();
                        acquired.set(command.wasAcquired());
                     } else {
                        acquired.set(false);
                     }
                  });

         acquired.waitForValue();
         logger.info("\tlock {} value {}", key, acquired.get());
      }
      if (acquired.get()) {
         logger.info("LOCKED {} ", key);
      }
      return acquired.get();
   }

   public void unlock() {
      logger.info("UNLOCKING {} ", key);
      raft.executeCommand(new UnlockCommand<TetrapodStateMachine>(key), e -> {
         if (e != null) {
            logger.info("UNLOCKED {} ", key);
         }
      });
   }

}
