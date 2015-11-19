package io.tetrapod.core.utils;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Provides a pool of locks to tune concurrent access to a number of objects.
 */
public class LockPool {

   private final ClosableLock[] locks;

   public LockPool() {
      this(256);
   }

   public LockPool(int size) {
      locks = new ClosableLock[size];
      for (int i = 0; i < size; i++)
         locks[i] = new ClosableLock(new ReentrantLock());
   }

   public synchronized ClosableLock getLock(int i) {
      i = Math.abs(i % locks.length);
      return locks[i];
   }

   public synchronized ClosableLock getLock(String str) {
      return getLock(str.hashCode());
   }

   public ClosableLock acquire(int i) {
      ClosableLock lock = getLock(i);
      lock.close();
      return lock;
   }

   public ClosableLock acquire(String str) {
      ClosableLock lock = getLock(str);
      lock.close();
      return lock;
   }

}
