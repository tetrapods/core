package io.tetrapod.core;

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
            if (!e.isQueueEmpty()) {
               service.dispatcher.dispatch(new Runnable() {
                  public void run() {
                     // we turn off auto flush in case we end up writing a lot 
                     final Session s = e.getSession();
                     if (s != null) {
                        // HACK--temporarily disabling this in prod to rule it out as a culprit
                        //s.setAutoFlush(false);
                     }
                     if (e.process()) {
                        if (s != null) {
                           s.flush();
                        }
                     }
                     if (s != null) {
                        s.setAutoFlush(true);
                     }
                  }
               });
            }
         }
         waitForWork();
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
