package io.tetrapod.core.pubsub;

import io.tetrapod.core.DefaultService;
import io.tetrapod.core.rpc.Message;
import io.tetrapod.protocol.core.Core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.*;

/**
 * Manages the pub-sub layer for a service
 * 
 * TODO:
 * <ul>
 * <li>All services connect to every tetrapod & messages routed accordingly
 * <li>tetrapods only gossip about each other, and services connect to all tetrapods to become available?
 * 
 * <li>Tetrapod sends client disconnection messages back to services with subscribers
 * <li>Tetrapod sends clients disconnection messages to clients when a service connection is lost
 * 
 * <li>Topics can buffer messages for short disconnections (maybe)
 * <li>Unit tests & documentation
 * </ul>
 */
public class Publisher {
   private static final Logger       logger       = LoggerFactory.getLogger(Publisher.class);

   private final DefaultService      service;

   private final AtomicInteger       topicCounter = new AtomicInteger();

   private final Map<Integer, Topic> topics       = new ConcurrentHashMap<>();

   public Publisher(DefaultService service) {
      this.service = service;
   }

   public Topic publish() {
      Topic topic = new Topic(this, topicCounter.incrementAndGet());
      topics.put(topic.topicId, topic);
      return topic;
   }

   public void subscribe(int topicId, int entityId, boolean once) {
      final Topic topic = topics.get(topicId);
      if (topic == null) {
         logger.warn("subscribe: Could not find topic for {}");
      }
      topic.subscribe(entityId, once);
   }

   public void unsubscribe(int topicId, int entityId) {
      final Topic topic = topics.get(topicId);
      topic.unsubscribe(entityId);
   }

   public int getEntityId() {
      return service.getEntityId();
   }

   public void sendMessage(Message msg) {
      sendMessage(msg, Core.UNADDRESSED);
   }

   public void sendMessage(Message msg, int toEntityId) {
      service.sendMessage(msg, toEntityId);
   }

   public void unpublish(int topicId) {
      final Topic topic = topics.remove(topicId);
      if (topic == null) {
         logger.warn("unpublish: Could not find topic for {}");
      }
      topic.unpublish();
   }

   public void broadcast(Message msg, int topicId) {
      final Topic topic = topics.get(topicId);
      if (topic == null) {
         logger.warn("broadcast: Could not find topic for {}");
      }
      topic.broadcast(msg);
   }

   protected void broadcastMessage(Message msg, int parentEntityId, int topicId) {
      // FIXME: handle allow-direct based on initial state...
      service.sendBroadcastMessage(msg, parentEntityId, topicId);
   }

}
