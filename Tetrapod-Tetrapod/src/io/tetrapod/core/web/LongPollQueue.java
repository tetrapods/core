package io.tetrapod.core.web;

import io.tetrapod.core.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LongPollQueue extends LinkedBlockingQueue<JSONObject> {
   private static final long                        serialVersionUID = 1L;

   protected static final Logger                    logger           = LoggerFactory.getLogger(LongPollQueue.class);

   private static final Map<Integer, LongPollQueue> queues           = new HashMap<>();

   public static LongPollQueue getQueue(int entityId, boolean createIfMissing) {
      synchronized (queues) {
         LongPollQueue q = queues.get(entityId);
         if (q == null && createIfMissing) {
            q = new LongPollQueue(entityId);
            queues.put(entityId, q);
         }
         return q;
      }
   }

   public static void clearEntity(Integer entityId) {
      synchronized (queues) {
         if (queues.remove(entityId) != null) {
            logger.info("Removing long poll queue for {}", entityId);
         }
      }
   }

   public synchronized void setLastDrain(long lastDrainTime) {
      this.lastDrainTime = lastDrainTime;
   }

   public synchronized long getLastDrainTime() {
      return lastDrainTime;
   }

   private final int entityId;
   private Lock      lock          = new ReentrantLock(true);
   private long      lastDrainTime = System.currentTimeMillis();

   public LongPollQueue(int entityId) {
      this.entityId = entityId;
   }

   public boolean tryLock() {
      return lock.tryLock();
   }

   public void unlock() {
      lock.unlock();
   }

   public int getEntityId() {
      return entityId;
   }

   public static void logStats() {
      logger.info("Long Poll Queues = {}", queues.size());
      synchronized (queues) {
         for (LongPollQueue q : queues.values()) {
            logger.info("Queue-{} = {} items", q.entityId, q.size());
         }
      }
   }

}
