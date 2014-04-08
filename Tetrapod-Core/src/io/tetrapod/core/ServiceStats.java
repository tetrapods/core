package io.tetrapod.core;

import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.*;
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

   private RateGauge                 rps              = new RateGauge(5);
   private RateGauge                 mps              = new RateGauge(5);

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
      service.sendRequest(new PublishRequest()).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            ResponseHandler.LOGGER.onResponse(res);
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
      rps.sample(service.getNumRequestsHandled());
      mps.sample(service.getNumMessagesSent());

      if (statsTopicId != null && statsSubscribers.size() > 0) {
         boolean dirty = false;
         int RPS = (int) rps.getAveragePerSecond();
         if (message.rps != RPS) {
            message.rps = RPS;
            dirty = true;
         }
         int MPS = (int) mps.getAveragePerSecond();
         if (message.mps != MPS) {
            message.mps = MPS;
            dirty = true;
         }
         if (dirty) {
            service.sendBroadcastMessage(message, statsTopicId);
         }
      }
   }

}
