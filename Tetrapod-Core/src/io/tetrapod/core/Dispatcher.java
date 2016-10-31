package io.tetrapod.core;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.tetrapod.core.rpc.ContextIdGenerator;
import io.tetrapod.core.tasks.TaskThreadPoolExecutor;
import io.tetrapod.core.utils.Util;

import java.util.Calendar;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.*;

import com.codahale.metrics.*;

/**
 * Manages service-wide thread-pools and dispatch of requests and sequenced behaviors
 */
public class Dispatcher {
   public static final Logger             logger                 = LoggerFactory.getLogger(Dispatcher.class);

   private final ThreadPoolExecutor       threadPool;
   private final ExecutorService          sequential;
   private final ScheduledExecutorService scheduled;
   private final ExecutorService          urgent;

   private final BlockingQueue<Runnable>  overflow               = new LinkedBlockingQueue<>();

   private EventLoopGroup                 bossGroup;
   private EventLoopGroup                 workerGroup;

   // metrics
   public final Counter                   workQueueSize          = Metrics.counter(this, "queue", "size");
   public final Timer                     requestTimes           = Metrics.timer(Dispatcher.class, "requests", "time");
   public final Meter                     requestsHandledCounter = Metrics.meter(Dispatcher.class, "requests", "count");
   public final Meter                     messagesSentCounter    = Metrics.meter(Dispatcher.class, "messages-sent", "count");

   public enum Priority {
      LOW, NORMAL, HIGH
   }

   public Dispatcher() {
      this(Util.getProperty("tetrapod.dispatcher.threads", 32));
   }

   public Dispatcher(int maxThreads) {
      logger.info("Dispatcher starting with {} threads", maxThreads);
      threadPool = new TaskThreadPoolExecutor(0, maxThreads, 5L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadFactory() {
         private final AtomicInteger counter = new AtomicInteger();

         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "Dispatch-Pooled-" + counter.incrementAndGet());
         }
      });

      sequential = new TaskThreadPoolExecutor(0, 1, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new ThreadFactory() {
         private final AtomicInteger counter = new AtomicInteger();

         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "Dispatch-Sequential-" + counter.incrementAndGet());
         }
      });

      scheduled = Executors.newScheduledThreadPool(1, new ThreadFactory() {
         private final AtomicInteger counter = new AtomicInteger();

         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "Dispatch-Scheduled-" + counter.incrementAndGet());
         }
      });

      urgent = new TaskThreadPoolExecutor(0, 4, 5L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadFactory() {
         private final AtomicInteger counter = new AtomicInteger();

         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "Dispatch-Urgent-" + counter.incrementAndGet());
         }
      });

   }

   public synchronized EventLoopGroup getWorkerGroup() {
      if (workerGroup == null || workerGroup.isShuttingDown()) {
         workerGroup = new NioEventLoopGroup();
      }
      assert (!workerGroup.isShutdown());
      assert (!workerGroup.isShuttingDown());
      return workerGroup;
   }

   public synchronized EventLoopGroup getBossGroup() {
      if (bossGroup == null || bossGroup.isShuttingDown()) {
         bossGroup = new NioEventLoopGroup();
      }
      assert (!bossGroup.isShutdown());
      assert (!bossGroup.isShuttingDown());
      return bossGroup;
   }

   public boolean dispatchHighPriority(final Runnable r) {
      return dispatch(r, Integer.MAX_VALUE, Priority.HIGH);
   }
   
   public boolean dispatch(final Runnable r) {
      return dispatch(r, Integer.MAX_VALUE, Priority.NORMAL);
   }

   public boolean dispatch(final Runnable r, final int overloadThreshold) {
      return dispatch(r, overloadThreshold, Priority.NORMAL);
   }

   /**
    * Dispatches a task on our thread-pool.
    * 
    * If we don't available threads, we post to a queue.
    * 
    * If the queue is > overloadThreshold, we will not queue, and will return false.
    */
   public boolean dispatch(final Runnable initialRunnable, final int overloadThreshold, final Priority priority) {
      assert initialRunnable != null;

      final Runnable r = () -> {
         ContextIdGenerator.clear();
         initialRunnable.run();
      };


      try {
         threadPool.submit(() -> processTask(r));
      } catch (RejectedExecutionException e) {
         if (priority == Priority.HIGH) {
            try {
               urgent.submit(r);
               return true;
            } catch (RejectedExecutionException ex) {
               // will then fall down to post on queue below
            }
         }
         if (overflow.size() < overloadThreshold && overflow.offer(r)) {
            workQueueSize.inc();
         } else {
            return false;
         }
      }
      return true;
   }

   public int getActiveThreads() {
      return threadPool.getActiveCount();
   }

   /**
    * Executes a task and then drains any overflow tasks
    */
   private void processTask(final Runnable task) {
      try {
         task.run();
      } catch (Throwable e) {
         logger.error(e.getMessage(), e);
      } finally {
         processOverflow();
      }
   }

   /**
    * Drain any items we've queued in the overflow queue
    */
   private void processOverflow() {
      Runnable r = overflow.poll();
      while (r != null) {
         workQueueSize.dec();
         try {
            ContextIdGenerator.clear();
            r.run();
         } catch (Throwable e) {
            logger.error(e.getMessage(), e);
         } finally {
            r = overflow.poll();
         }
      }
   }

   /**
    * Queues a task for sequential dispatch
    */
   public boolean dispatchSequential(Runnable r) {
      assert r != null;
      try {
         sequential.execute(r);
      } catch (RejectedExecutionException e) {
         if (overflow.offer(r)) {
            workQueueSize.inc();
         } else {
            return false;
         }
      }
      return true;
   }

   /**
    * Schedule a task to be executed on the thread-pool at a later time
    */
   public ScheduledFuture<?> dispatch(int delay, TimeUnit unit, final Runnable r) {
      if (!scheduled.isShutdown()) {
         return scheduled.schedule(() -> dispatch(r), delay, unit);
      } else {
         return null;
      }
   }

   /**
    * Schedule a task to be executed on the thread-pool at the same time every day. 
    * Note: Doesn't take daylight savings into account.
    */
   public ScheduledFuture<?> dispatchDaily(int hourOfTheDay, final Runnable r) {
      assert (!scheduled.isShutdown());

      Calendar calendar = Calendar.getInstance();
      int hour = calendar.get(Calendar.HOUR_OF_DAY);
      int minutes = calendar.get(Calendar.MINUTE);

      int minsAfter12AM = hour * 60 + minutes;
      long delay = hour < hourOfTheDay ? ((hourOfTheDay * 60) - minsAfter12AM) : (Util.MINS_IN_A_DAY - minsAfter12AM + (hourOfTheDay * 60));
      
      return scheduled.scheduleAtFixedRate(() -> dispatch(r), delay, Util.MINS_IN_A_DAY, TimeUnit.MINUTES); 
   }   
   
   /**
    * Cleanly terminate all thread-pools
    */
   public void shutdown() {
      threadPool.shutdown();
      sequential.shutdown();
      scheduled.shutdown();
      urgent.shutdown();
      if (bossGroup != null) {
         bossGroup.shutdownGracefully();
      }
      if (workerGroup != null) {
         workerGroup.shutdownGracefully();
      }
      synchronized (this) {
         bossGroup = null;
         workerGroup = null;
      }
   }

   public boolean isRunning() {
      return !threadPool.isShutdown();
   }

}
