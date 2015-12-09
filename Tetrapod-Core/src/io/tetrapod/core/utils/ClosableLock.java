package io.tetrapod.core.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

public class ClosableLock implements AutoCloseable, Lock {
   final Lock lock;

   public ClosableLock(Lock lock) {
      this.lock = lock;
   }

   @Override
   public void close() {
      lock.unlock();
   }

   @Override
   public void lock() {
      this.lock.lock();
   }

   @Override
   public void lockInterruptibly() throws InterruptedException {
      this.lock.lockInterruptibly();
   }

   @Override
   public boolean tryLock() {
      return this.lock.tryLock();
   }

   @Override
   public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
      return this.lock.tryLock(time, unit);
   }

   @Override
   public void unlock() {
      this.lock.unlock();
   }

   @Override
   public Condition newCondition() {
      return this.lock.newCondition();
   }

   public static ClosableLock acquire(final Lock lock) {
      ClosableLock cl = new ClosableLock(lock);
      cl.lock();
      return cl;
   }

   public static ClosableLock acquireReadLock(final ReadWriteLock lock) {
      ClosableLock cl = new ClosableLock(lock.readLock());
      cl.lock();
      return cl;
   }

   public static ClosableLock acquireWriteLock(final ReadWriteLock lock) {
      ClosableLock cl = new ClosableLock(lock.writeLock());
      cl.lock();
      return cl;
   }
}
