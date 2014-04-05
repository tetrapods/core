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
            if (e.getQueueLength() > 0) {
               service.dispatcher.dispatch(new Runnable() {
                  public void run() {
                     e.process();
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
