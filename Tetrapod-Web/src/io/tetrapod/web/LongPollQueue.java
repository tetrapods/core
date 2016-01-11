package io.tetrapod.web;

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

   public static LongPollQueue getQueue(int entityId) {
      synchronized (queues) {
         LongPollQueue q = queues.get(entityId);
         if (q == null) {
            q = new LongPollQueue(entityId);
            queues.put(entityId, q);
         }
         return q;
      }
   }

   public static void clearEntity(int entityId) {
      synchronized (queues) {
         if (queues.remove(entityId) != null) {
            logger.debug("Removing long poll queue for {}", entityId);
         }
      }
   }

   private final int entityId;
   private Lock      lock = new ReentrantLock(true);

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

}
