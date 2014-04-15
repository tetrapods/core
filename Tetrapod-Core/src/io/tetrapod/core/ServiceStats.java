package io.tetrapod.core;

import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.service.ServiceStatsMessage;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.slf4j.*;

/**
 * Stores basic service stats & handles stats publication
 */
public class ServiceStats {
   private static final Logger       logger           = LoggerFactory.getLogger(ServiceStats.class);

   private final Set<Integer>        statsSubscribers = new HashSet<>();

   private final DefaultService      service;

   private Integer                   statsTopicId;

   private final ServiceStatsMessage message          = new ServiceStatsMessage();

   public ServiceStats(DefaultService service) {
      this.service = service;
      scheduleUpdate();
   }

   /**
    * publish the service stats, and subscribe any pending subscribers
    */
   protected void publishTopic() {
      message.entityId = service.getEntityId();
      service.sendDirectRequest(new PublishRequest()).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            if (!res.isError()) {
               setTopic(((PublishResponse) res).topicId);
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
         service.sendMessage(message, entityId, statsTopicId);
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
         if (dirty) {
            service.sendBroadcastMessage(message, statsTopicId);
         }
      }
   }
}
