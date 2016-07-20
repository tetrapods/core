package io.tetrapod.core;

import java.util.Queue;

import io.tetrapod.core.registry.EntityInfo;

public class TetrapodWorker implements Runnable {

   public final TetrapodService service;

   public TetrapodWorker(TetrapodService service) {
      this.service = service;
      Thread t = new Thread(this, "TetrapodWorker");
      t.start();
   }

   @Override
   public void run() {
      while (!service.isShuttingDown()) {
         for (final EntityInfo e : service.registry.getEntities()) {
            drainQueue(e);
         }
         Queue<EntityInfo> deleted = service.registry.getDeletedEntities();
         while (!deleted.isEmpty()) {
            EntityInfo e = deleted.poll();
            if (e != null) {
               drainQueue(e);
            }
         }
         waitForWork();
      }
   }

   private void drainQueue(EntityInfo e) {
      if (!e.isQueueEmpty()) {
         service.dispatcher.dispatch(() -> {
            // we turn off auto flush in case we end up writing a lot 
            final Session s = e.getSession();
            if (s != null) {
               s.setAutoFlush(false);
            }
            if (e.process()) {
               if (s != null) {
                  s.flush();
               }
            }
            if (s != null) {
               s.setAutoFlush(true);
            }
         });
      }
   }

   public synchronized void waitForWork() {
      try {
         wait(250);
      } catch (InterruptedException e) {}
   }

   public synchronized void kick() {
      notifyAll();
   }

}
