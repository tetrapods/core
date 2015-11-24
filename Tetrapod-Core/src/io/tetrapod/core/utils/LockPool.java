package io.tetrapod.core.utils;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Provides a pool of locks to tune concurrent access to a number of objects.
 */
public class LockPool {

   private final ReentrantLock[] locks;

   public LockPool() {
      this(256);
   }

   public LockPool(int size) {
      locks = new ReentrantLock[size];
      for (int i = 0; i < size; i++)
         locks[i] = new ReentrantLock();
   }

   public synchronized ReentrantLock getLock(int i) {
      i = Math.abs(i % locks.length);
      return locks[i];
   }

   public synchronized ReentrantLock getLock(String str) {
      return getLock(str.hashCode());
   }

   public ClosableLock acquire(int i) {
      ClosableLock lock = new ClosableLock(getLock(i));
      lock.lock();
      return lock;
   }

   public ClosableLock acquire(String str) {
      ClosableLock lock = new ClosableLock(getLock(str));
      lock.lock();
      return lock;
   }

}
