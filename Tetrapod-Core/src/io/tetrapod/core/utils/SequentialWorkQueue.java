package io.tetrapod.core.utils;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.*;

import org.slf4j.*;

/**
 * A sequential queue of Runnable tasks. Appending is non-blocking, consuming can be called by any threads, but queued items will be
 * serialized.
 * 
 * TODO:
 * <ul>
 * <li>Add warning thresholds and hard limits
 * </ul>
 * 
 */
public class SequentialWorkQueue {

   public static final Logger      logger       = LoggerFactory.getLogger(SequentialWorkQueue.class);

   protected final Lock            consumerLock = new ReentrantLock();
   protected final Queue<Runnable> queue        = new ConcurrentLinkedQueue<>();

   public void queue(final Runnable task) {
      queue.add(task);
   }

   public boolean isQueueEmpty() {
      return queue.isEmpty();
   }

   /**
    * Process the pending work queued for this entity.
    * 
    * @return true if any queued work was processed.
    */
   public boolean process() {
      boolean processedSomething = false;
      if (consumerLock.tryLock()) {
         try {
            Runnable task = null;
            do {
               task = queue.poll();
               if (task != null) {
                  processedSomething = true;
                  try {
                     task.run();
                  } catch (Throwable e) {
                     logger.error(e.getMessage(), e);
                  }
               }
            } while (task != null);
         } finally {
            consumerLock.unlock();
         }
      }
      return processedSomething;
   }

}
