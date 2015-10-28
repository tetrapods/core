package io.tetrapod.core;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.tetrapod.core.pubsub.Topic;
import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.*;

/**
 * Stores basic service stats & handles stats publication
 */
public class ServiceStats implements TopicUnsubscribedMessage.Handler {
   private static final Logger             logger   = LoggerFactory.getLogger(ServiceStats.class);

   private final DefaultService            service;
   private final Topic                     statsTopic;
   private final ServiceStatsMessage       message  = new ServiceStatsMessage();
   private final RequestStats              requests = new RequestStats();
   private final Map<String, RequestStats> domains  = new HashMap<>();

   public ServiceStats(DefaultService service) {
      this.service = service;
      this.statsTopic = service.publishTopic();
      scheduleUpdate();
      register(requests, "Requests");

   //   service.addMessageHandler(new TopicUnsubscribedMessage(), this);
   }

   
   
   /**
    * publish the service stats, and subscribe any pending subscribers
    */
   protected void publishTopic() {
      message.entityId = service.getEntityId();
   }

   /**
    * Subscribe an entity to our topic
    */
   protected synchronized void subscribe(int entityId) {
      statsTopic.subscribe(entityId, true);
      statsTopic.sendMessage(message, entityId);
   }

   /**
    * Unsubscribe an entity to our topic
    */
   protected synchronized void unsubscribe(int entityId) {
      statsTopic.unsubscribe(entityId);
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
         service.dispatcher.dispatch(3, TimeUnit.SECONDS, new Runnable() {
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
      if (statsTopic.numSubscribers() > 0) {
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
            statsTopic.broadcast(message);
         }
      }
   }

   public void recordRequest(int fromEntityId, Request req, long nanos) {
      requests.recordRequest(fromEntityId, req.getClass().getSimpleName().replaceAll("Request", ""), nanos);
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
      }
      return res;
   }

   public void register(RequestStats stats, String name) {
      synchronized (domains) {
         domains.put(name, stats);
      }
   }

   @Override
   public void genericMessage(Message message, MessageContext ctx) {
      // TODO Auto-generated method stub
      
   }

   @Override
   public void messageTopicUnsubscribed(TopicUnsubscribedMessage m, MessageContext ctx) {
      // TODO Auto-generated method stub
      
   }

}
