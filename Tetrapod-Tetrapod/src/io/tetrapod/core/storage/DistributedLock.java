package io.tetrapod.core.storage;

import io.tetrapod.core.utils.Value;
import io.tetrapod.raft.*;
import io.tetrapod.raft.RaftRPC.ClientResponseHandler;
import io.tetrapod.raft.storage.*;

import java.io.*;
import java.util.UUID;

import org.slf4j.*;

/**
 * TODO: implement uuid key for lock command to be idempotent
 */
public class DistributedLock implements Closeable {

   private static final Logger logger = LoggerFactory.getLogger(DistributedLock.class);
   final RaftStorage           raft;
   final String                key;
   final String                uuid;

   public DistributedLock(String key, RaftStorage raft) {
      this.key = key;
      this.raft = raft;
      this.uuid = UUID.randomUUID().toString();
   }

   public boolean lock(long leaseForMillis, long waitForMillis) {
      final long started = System.currentTimeMillis();
      logger.info("LOCKING {} ...", key);
      final Value<Boolean> acquired = new Value<Boolean>(false);
      while (!acquired.get() && started + waitForMillis > System.currentTimeMillis()) {
         raft.executeCommand(new LockCommand<TetrapodStateMachine>(key, leaseForMillis), new ClientResponseHandler<TetrapodStateMachine>() {
            @Override
            public void handleResponse(Entry<TetrapodStateMachine> e) {
               if (e != null) {
                  final LockCommand<TetrapodStateMachine> command = (LockCommand<TetrapodStateMachine>) e.getCommand();
                  acquired.set(command.wasAcquired());
               } else {
                  acquired.set(false);
               }
            }
         });

         acquired.waitForValue();
         logger.info("\tlock {} value {}", key, acquired.get());
      }
      logger.info("LOCKED {} ", key);
      return acquired.get();
   }

   public void unlock() {
      logger.info("UNLOCKING {} ", key);
      raft.executeCommand(new UnlockCommand<TetrapodStateMachine>(key), new ClientResponseHandler<TetrapodStateMachine>() {
         @Override
         public void handleResponse(Entry<TetrapodStateMachine> e) {
            if (e != null) {
               logger.info("UNLOCKED {} ", key);
            }
         }
      });
   }

   @Override
   public void close() throws IOException {
      unlock();
   }

}
