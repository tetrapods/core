package io.tetrapod.core;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.*;

/**
 * Stores basic service stats & handles stats publication
 */
public class ServiceStats {
   private static final Logger logger = LoggerFactory.getLogger(ServiceStats.class);

   private final Set<Integer> statsSubscribers = new HashSet<>();

   private final DefaultService service;

   private Integer statsTopicId;

   private final ServiceStatsMessage message = new ServiceStatsMessage();

   public ServiceStats(DefaultService service) {
      this.service = service;
      scheduleUpdate();
   }

   /**
    * publish the service stats, and subscribe any pending subscribers
    */
   protected void publishTopic() {
      message.entityId = service.getEntityId();
      service.sendDirectRequest(new PublishRequest(1)).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            if (!res.isError()) {
               setTopic(((PublishResponse) res).topicIds[0]);
            }
         }
      });
   }

   private synchronized void setTopic(int topicId) {
      statsTopicId = topicId;
      for (int entityId : statsSubscribers) {
         service.subscribe(statsTopicId, entityId);
      }
      service.sendBroadcastMessage(message, statsTopicId);
   }

   /**
    * Subscribe an entity to our topic
    */
   protected synchronized void subscribe(int entityId) {
      statsSubscribers.add(entityId);
      if (statsTopicId != null) {
         service.subscribe(statsTopicId, entityId);
         service.sendMessage(message, entityId);
      }
   }

   /**
    * Unsubscribe an entity to our topic
    */
   protected synchronized void unsubscribe(int entityId) {
      statsSubscribers.remove(entityId);
      if (statsTopicId != null) {
         service.unsubscribe(statsTopicId, entityId);
      }
   }

   /**
    * As long as service is running, schedule a stats poll periodically
    */
   private void scheduleUpdate() {
      if (!service.isShuttingDown()) {
         try {
            updateStats();
         } catch (Throwable e) {
            logger.error(e.getMessage(), e);
         }
         service.dispatcher.dispatch(2, TimeUnit.SECONDS, new Runnable() {
            public void run() {
               scheduleUpdate();
            }
         });
      }
   }

   /**
    * Update our stats counters and broadcast any updates to subscribers
    */
   private synchronized void updateStats() {
      if (statsTopicId != null && statsSubscribers.size() > 0) {
         boolean dirty = false;
         int RPS = (int) service.getDispatcher().requestsHandledCounter.getOneMinuteRate();
         if (message.rps != RPS) {
            message.rps = RPS;
            dirty = true;
         }
         int MPS = (int) service.getDispatcher().messagesSentCounter.getOneMinuteRate();

         if (message.mps != MPS) {
            message.mps = MPS;
            dirty = true;
         }

         long latency = service.getAverageResponseTime();
         if (message.latency != latency) {
            message.latency = latency;
            dirty = true;
         }
         long counter = service.getCounter();
         if (message.counter != counter) {
            message.counter = counter;
            dirty = true;
         }

         byte memory = (byte) Math.round(100 * Metrics.getUsedMemory());
         if (message.memory != memory) {
            message.memory = memory;
            dirty = true;
         }

         int threads = Math.round(Metrics.getThreadCount());
         if (message.threads != threads) {
            message.threads = threads;
            dirty = true;
         }

         if (dirty) {
            service.sendBroadcastMessage(message, statsTopicId);
         }
      }
   }

   public static class ReqSample {
      public final String key;
      public final long   timestamp;
      public final long   execution;

      public ReqSample(int fromEntityId, Request req, long millis) {
         this.key = req.getClass().getSimpleName().replaceAll("Request", "");
         this.timestamp = System.currentTimeMillis();
         this.execution = millis;
      }
   }

   public static final int       MAX_REQUEST_SAMPLES = 1024 * 64;
   public final Queue<ReqSample> requests            = new ConcurrentLinkedQueue<>();

   public void recordRequest(int fromEntityId, Request req, long nanos) {
      requests.add(new ReqSample(fromEntityId, req, nanos));
      if (requests.size() > MAX_REQUEST_SAMPLES) {
         requests.remove();
      }
   }

   public ServiceRequestStatsResponse getRequestStats(int limit, long minTime) {
      final Map<String, Integer> counts = new HashMap<>();
      final Map<String, Long> execution = new HashMap<>();
      long minTimestamp = Long.MAX_VALUE;
      for (ReqSample sample : requests) {
         if (sample.timestamp >= minTime) {
            minTimestamp = Math.min(minTimestamp, sample.timestamp);

            // accumulate invocations count
            Integer count = counts.get(sample.key);
            if (count == null) {
               count = 1;
            } else {
               count = count + 1;
            }
            counts.put(sample.key, count);

            // accumulate microsecond totals
            Long time = execution.get(sample.key);
            if (time == null) {
               time = sample.execution / 1000;
            } else {
               time = time + sample.execution / 1000;
            }
            execution.put(sample.key, time);
         }
      }

      // sort by invocations
      final List<String> sorted = new ArrayList<>(counts.keySet());
      Collections.sort(sorted, new Comparator<String>() {
         public int compare(String a, String b) {
            if (counts.get(a) >= counts.get(b)) {
               return -1;
            } else {
               return 1;
            }
         }
      });

      final List<RequestStat> stats = new ArrayList<RequestStat>();
      for (String key : sorted) {
         stats.add(new RequestStat(key, counts.get(key), execution.get(key)));
         if (stats.size() >= limit)
            break;
      }

      return new ServiceRequestStatsResponse(stats, minTimestamp);
   }

}
