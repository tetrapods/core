package io.tetrapod.core;

import java.io.*;

public class DistributedLock implements Closeable {
   final RaftStorage raft;
   final String      key;

   public DistributedLock(String key, RaftStorage raft) {
      this.key = key;
      this.raft = raft;      
   }

   public void lock(int millis) {
     // raft.lock(key, millis);
   }

   public void tryLock(int millis) {

   }

   private void unlock() {

   }

   @Override
   public void close() throws IOException {
      unlock();
   }

}
