package io.tetrapod.core.tasks;

import io.tetrapod.core.ServiceException;
import io.tetrapod.core.utils.DiagnosticCommand;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This is a lock object that is safe to acquire and release from separate threads, thus is appropriate for async operations.
 * To lock this, you should call one of the lock function if this is single threaded, or lockAsync if you want an
 * entire task chain synchronized.   You can also call the readLock and readLockAsync methods which allows multiple
 * concurrent "read" processes to execute but not at the same time as any normal lock is executing.  In other words,
 * all read locks may execute in parallel -or- a single normal lock.
 *
 * @author paulm
 *         Created: 9/8/16
 */
public class AsyncLock  {
   private static final short MAX = Short.MAX_VALUE;
   private final Semaphore semaphore;
   private final String name;

   /**
    * Creates an async lock
    * @param name A name used for logging when there are issues acquire locking
    */
   public AsyncLock(String name) {
      this.name = name;
      this.semaphore = new Semaphore(MAX, true);
   }

   /**
    * Use this to wrap a function that returns a value in a sync block.  Don't return Tasks through this method.  Use
    * lockAsync instead.
    * @param function   A function you wish to be synchronized
    * @param <T> The type of object that is being returned
    * @return The return value from the fuction
    */
   public <T> T lock(Func0<T> function) {
      return lock(MAX, function);
   }
   /**
    * Use this to wrap a function that returns a value in a in a non-exclusive read lock.  Don't return Tasks through this method.  Use
    * lockAsync instead.  Multiple read locks can be acquired at the same time, but will be blocked by and will block a 'standard' lock.
    * @param function   A function you wish to be in a read lock
    * @param <T> The type of object that is being returned
    * @return The return value from the fuction
    */
   public <T> T readLock(Func0<T> function) {
      return lock(1, function);
   }

   private <T> T lock(int permits, Func0<T> function) {
      acquire(permits);
      try {
         T ret = function.apply();
         if (ret instanceof Task) {
            throw new IllegalArgumentException("You must call lockAsync if you want to return a task and expect the " +
                    "locking semantics to extend across the task chain:  Lock: " + name);
         }
         return ret;
      } finally {
         release(permits);
      }
   }

   /**
    * Use this to wrap a runnable in a sync block
    * @param runnable  The runnable to execute within the sync
    */
   public void lock(Runnable runnable) {
      lock(MAX, runnable);
   }

   /**
    * Use this to wrap a runnable in a non-exclusive read lock.  Multiple read locks can be acquired at the
    * same time, but will be blocked by and will block a 'standard' lock.
    * @param runnable
    */
   public void readLock(Runnable runnable) {
      lock(1, runnable);
   }
   private void lock(int permits, Runnable runnable) {
      acquire(permits);
      try {
         runnable.run();
      } finally {
         release(permits);
      }
   }


   /**
    * Use this function to sync an entire task chain, across async threads
    * @param function  The function that returns a Task
    * @param <T>  The type of value wrapped by the task.
    * @return  The task that was returned from the function
    */
   public <T> Task<T> lockAsync(Func0<Task<T>> function) {
      return lockAsync(MAX, function);
   }

   /**
    * Use this function to get a non exclusive lock across async threads.  Multiple read locks can be acquired at the
    * same time, but will be blocked by and will block a 'standard' lock.
    * @param function
    * @param <T>
    * @return
    */
   public <T> Task<T> readLockAsync(Func0<Task<T>> function) {
      return lockAsync(1, function);
   }
   public <T> Task<T> lockAsync(LockMode lockMode, Func0<Task<T>> function) {
      if (lockMode == LockMode.READ) {
         return readLockAsync(function);
      } else if (lockMode == LockMode.WRITE){
         return lockAsync(function);
      } else {
         return function.apply();
      }
   }


   private <T> Task<T> lockAsync(final int permits, Func0<Task<T>> function) {
      acquire(permits);

      try {
         return function.apply().thenApply((resp) -> {
            release(permits);
            return resp;
         }).exceptionally(th -> {
            release(permits);
            throw ServiceException.wrapIfChecked(th);
         });
      } catch (Throwable e) {
         release(permits);
         throw ServiceException.wrapIfChecked(e);
      }
   }
   private void release(int permits) {
      if (permits == MAX) {
         lockingContextId = null;
      }
      semaphore.release(permits);
   }


   /**
    * This allows you to acquire a lock.  This method is only exposed to allow it to work with try with resource block.
    * Do something like this:
    *
    * //async lock defined earlier
    * AsyncLock myLock = new AsyncLock();
    * ...
    *
    * try (AsyncLock l = myLock.acquire()) {
    *    //things in here are synchronized
    * }
    *
    *
    * @return  This object.  Return value required for try with resources to work but can be ignored
    */
   private AsyncLock acquire(int permits) {
      return acquire(permits, 30, TimeUnit.SECONDS);
   }

   private AsyncLock acquire(int permits, int timeout, TimeUnit timeoutUnits) {
      try {
         if (!semaphore.tryAcquire(permits, timeout, timeoutUnits)) {
            synchronized (this) {
               if (System.currentTimeMillis() > lastDump + 5000) {
                  lastDump = System.currentTimeMillis();
                  DiagnosticCommand.consoleDump();
                  DiagnosticCommand.loggerDump();
               }
            }

            if (TaskContext.hasCurrent()) {
               String myContextId = TaskContext.get("contextId");
               if (myContextId != null && myContextId.equals(lockingContextId)) {
                  throw new TimeoutException("Attempt at re-entrant async lock acquire.  Could allow if we decided we wanted to. Lock: " + name + " contextId " + myContextId);
               }
            }

            throw new TimeoutException("Unable to acquire lock in time.  Likely a long running lock holder.  Lock: " + name);
         }
         if (permits == MAX && TaskContext.hasCurrent()) {
            lockingContextId = TaskContext.get("contextId"); //slight leak in encapsulation.  This requires contextId set earlier in framework and is passed across process boundaries
         }
         return this;
      } catch (Throwable e) {
         throw ServiceException.wrapIfChecked(e);
      }
   }

   public enum LockMode {
      NONE,
      READ,
      WRITE
   }

   private String lockingContextId;
   private volatile long lastDump = 0;

   public static void main(String[] args) {

      TaskContext.wrapPushPop(() -> {
         TaskContext.set("contextId", "1234");
         AsyncLock lock = new AsyncLock("hey");
         Task.runAsync(()-> {
            lock.lockAsync(()-> {
                       return lock.lockAsync(Task::done);
                    }
            ).join();
         }).join();
      }).run();

   }

}


