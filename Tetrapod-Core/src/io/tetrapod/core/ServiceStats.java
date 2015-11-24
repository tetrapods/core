package io.tetrapod.core;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Gauge;

import io.tetrapod.core.rpc.Request;
import io.tetrapod.protocol.core.*;

/**
 * Stores basic service stats & handles stats publication
 */
public class ServiceStats {
   private static final Logger             logger           = LoggerFactory.getLogger(ServiceStats.class);

   private final Set<Integer>              statsSubscribers = new HashSet<>();

   private final DefaultService            service;

   private Integer                         statsTopicId;

   private final ServiceStatsMessage       message          = new ServiceStatsMessage();

   private final RequestStats              requests         = new RequestStats();
   private final Map<String, RequestStats> domains          = new HashMap<>();

   public ServiceStats(DefaultService service) {
      this.service = service;
      scheduleUpdate();
      register(requests, "Requests");

      Metrics.register(new Gauge<Double>() {
         @Override
         public Double getValue() {
            return requests.getErrorRate();
         }
      }, this, "errors");
   }

   /**
    * publish the service stats, and subscribe any pending subscribers
    */
   protected void publishTopic() {
      message.entityId = service.getEntityId();
      service.sendDirectRequest(new PublishRequest(1)).handle(res -> {
         if (!res.isError()) {
            setTopic(((PublishResponse) res).topicIds[0]);
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
         service.dispatcher.dispatch(3, TimeUnit.SECONDS, () -> scheduleUpdate());
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

   public void recordRequest(int fromEntityId, Request req, long nanos, int result) {
      requests.recordRequest(fromEntityId, req.getClass().getSimpleName().replaceAll("Request", ""), nanos, result);
   }

   public ServiceRequestStatsResponse getRequestStats(String domain, int limit, long minTime, RequestStatsSort sortBy) {
      ServiceRequestStatsResponse res = null;
      if (domain == null || domain.isEmpty()) {
         res = requests.getRequestStats(limit, minTime, sortBy);
      } else {
         res = domains.get(domain).getRequestStats(limit, minTime, sortBy);
      }
      synchronized (domains) {
         res.domains = domains.keySet().toArray(new String[domains.size()]);
         Arrays.sort(res.domains, (a, b) -> {
            if (a.equals("Requests"))
               return -1;
            if (b.equals("Requests"))
               return 1;
            return a.compareTo(b);
         });
      }
      return res;
   }

   public void register(RequestStats stats, String name) {
      synchronized (domains) {
         domains.put(name, stats);
      }
   }

}
