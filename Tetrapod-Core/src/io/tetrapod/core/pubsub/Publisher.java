package io.tetrapod.core.pubsub;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.tetrapod.core.DefaultService;
import io.tetrapod.core.rpc.Message;
import io.tetrapod.core.rpc.MessageContext;
import io.tetrapod.protocol.core.*;

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
public class Publisher implements TopicUnsubscribedMessage.Handler, TopicNotFoundMessage.Handler, SubscriberNotFoundMessage.Handler {
   private static final Logger       logger       = LoggerFactory.getLogger(Publisher.class);

   private final DefaultService      service;

   private final AtomicInteger       topicCounter = new AtomicInteger();

   private final Map<Integer, Topic> topics       = new ConcurrentHashMap<>();

   public Publisher(DefaultService service) {
      this.service = service;
      service.addMessageHandler(new TopicUnsubscribedMessage(), this);
      //service.addMessageHandler(new TopicUnpublishedMessage(), this);
      service.addMessageHandler(new TopicNotFoundMessage(), this);
      service.addMessageHandler(new SubscriberNotFoundMessage(), this);
   }

   public Topic publish() {
      Topic topic = new Topic(this, topicCounter.incrementAndGet());
      topics.put(topic.topicId, topic);
      return topic;
   }

   public void subscribe(int topicId, int entityId, boolean once) {
      final Topic topic = topics.get(topicId);
      if (topic == null) {
         logger.warn("subscribe: Could not find topic for {}", topicId);
      }
      topic.subscribe(entityId, once);
   }

   public void unsubscribe(int topicId, int entityId) {
      final Topic topic = topics.get(topicId);
      if (topic != null) {
         topic.unsubscribe(entityId);
      } else {
         logger.warn("unsubscribe: Could not find topic for {} ", topicId);
      }
   }

   public int getEntityId() {
      return service.getEntityId();
   } 

   public boolean sendMessage(Message msg, int toEntityId, int topicId) {
      return service.sendPrivateMessage(msg, toEntityId, topicId);
   }

   public void unpublish(int topicId) {
      final Topic topic = topics.remove(topicId);
      if (topic == null) {
         logger.warn("unpublish: Could not find topic for {}", topicId);
      }
      topic.unpublishAll();
   }

   public void broadcast(Message msg, int topicId) {
      final Topic topic = topics.get(topicId);
      if (topic == null) {
         logger.warn("broadcast: Could not find topic for {}", topicId);
      }
      topic.broadcast(msg);
   }

   protected boolean broadcastMessage(Message msg, int parentEntityId, int topicId) {
      return service.sendBroadcastMessage(msg, parentEntityId, topicId);
   }

   public void resetTopics() {
      for (Topic t : topics.values()) {
         t.reset();
      }
   }

   @Override
   public void genericMessage(Message message, MessageContext ctx) {}

   @Override
   public void messageTopicUnsubscribed(TopicUnsubscribedMessage m, MessageContext ctx) {
      if (m.publisherId == service.getEntityId()) {
         logger.info("@@@@@ UNSUBSCRIBING DISCONNECTED SUBSCRIBER {}", m.dump());
         unsubscribe(m.topicId, m.entityId);
      }
   }

   //   @Override
   //   public void messageTopicUnpublished(TopicUnpublishedMessage m, MessageContext ctx) {
   //      //logger.info("@@@@@ {} {}", m.dump(), ctx.header.dump());
   //      if (m.publisherId == service.getEntityId()) {
   //         // call topic unpublish listener
   //         // re-publish
   //         final Topic orig = topics.get(m.topicId);
   //         if (orig != null) {
   //            logger.info("@@@@@ REPUBLISHING {}", orig);
   //            final Topic topic = new Topic(this, orig.topicId);
   //            for (Topic.Subscriber s : orig.getSubscribers()) {
   //               topic.subscribe(s.entityId, true);
   //            }
   //            topics.put(topic.topicId, topic);
   //         }
   //      }
   //   }

   public void unsubscribeFromAllTopics(int entityId) {
      for (Topic t : topics.values()) {
         t.unsubscribe(entityId);
      }
   }

   /**
    * Sent from a tetrapod when it receives a broadcast for a topic it doesn't currently track
    */
   @Override
   public void messageTopicNotFound(TopicNotFoundMessage m, MessageContext ctx) {
      if (m.publisherId == service.getEntityId()) {
         final Topic topic = topics.get(m.topicId);
         if (topic != null) {
            // FIXME
            topic.reset(ctx.header.fromId);
         }
      }
   }

   /**
    * Sent from a tetrapod when it receives a subscribe message for an entity it doesn't have
    */
   @Override
   public void messageSubscriberNotFound(SubscriberNotFoundMessage m, MessageContext ctx) {
      if (m.publisherId == service.getEntityId()) {
         unsubscribe(m.topicId, m.entityId);
      }
   }

}