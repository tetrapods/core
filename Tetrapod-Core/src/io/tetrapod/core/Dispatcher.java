package io.tetrapod.core;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.tetrapod.core.utils.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.*;

/**
 * Manages service-wide thread-pools and dispatch of requests and sequenced behaviors
 */
public class Dispatcher {
   public static final Logger             logger                 = LoggerFactory.getLogger(Dispatcher.class);

   private final ThreadPoolExecutor       threadPool;
   private final ExecutorService          sequential;
   private final ScheduledExecutorService scheduled;

   public final Gauge                     requestTimes           = new Gauge(128);
   public final Accumulator               requestsHandledCounter = new Accumulator();
   public final Accumulator               messagesSentCounter    = new Accumulator();

   private final BlockingQueue<Runnable>  overflow               = new LinkedBlockingQueue<>();

   private EventLoopGroup                 workerGroup;

   public Dispatcher() {
      this(8);
   }

   public Dispatcher(int maxThreads) {
      threadPool = new ThreadPoolExecutor(0, maxThreads, 5L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
         private final AtomicInteger counter = new AtomicInteger();

         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "Dispatch-Pooled-" + counter.incrementAndGet());
         }
      });

      sequential = new ThreadPoolExecutor(0, 1, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
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
   }

   public synchronized EventLoopGroup getWorkerGroup() {
      if (workerGroup == null) {
         workerGroup = new NioEventLoopGroup();
      }
      return workerGroup;
   }

   /**
    * Dispatches a task on our thread-pool.
    */
   public void dispatch(final Runnable r) {
      assert r != null;
      try {
         threadPool.submit(new Runnable() {
            public void run() {
               processTask(r);
            }
         });
      } catch (RejectedExecutionException e) {
         overflow.add(r);
      }
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
         try {
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
   public void dispatchSequential(Runnable r) {
      assert r != null;
      try {
         sequential.execute(r);
      } catch (RejectedExecutionException e) {
         overflow.add(r);
      }
   }

   /**
    * Schedule a task to be executed on the thread-pool at a later time
    */
   public ScheduledFuture<?> dispatch(int delay, TimeUnit unit, final Runnable r) {
      return scheduled.schedule(new Runnable() {
         public void run() {
            dispatch(r);
         }
      }, delay, unit);
   }

   /**
    * Cleanly terminate all thread-pools
    */
   public void shutdown() {
      threadPool.shutdown();
      sequential.shutdown();
      scheduled.shutdown();
      if (workerGroup != null) {
         workerGroup.shutdownGracefully();
      }
   }

   public boolean isRunning() {
      return !threadPool.isShutdown();
   }

}
