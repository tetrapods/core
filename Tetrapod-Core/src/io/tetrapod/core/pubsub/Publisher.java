package io.tetrapod.core.pubsub;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.tetrapod.core.DefaultService;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.tasks.TaskContext;
import io.tetrapod.protocol.core.*;

/**
 * Manages the pub-sub layer for a service
 */
public class Publisher implements TopicUnsubscribedMessage.Handler, TopicNotFoundMessage.Handler, TopicUnpublishedMessage.Handler,
      SubscriberNotFoundMessage.Handler, ServiceRemovedMessage.Handler {
   private static final Logger       logger       = LoggerFactory.getLogger(Publisher.class);

   private final DefaultService      service;

   private final AtomicInteger       topicCounter = new AtomicInteger();

   private final Map<Integer, Topic> topics       = new ConcurrentHashMap<>();

   public Publisher(DefaultService service) {
      this.service = service;
      service.addMessageHandler(new TopicUnsubscribedMessage(), this);
      service.addMessageHandler(new TopicUnpublishedMessage(), this);
      service.addMessageHandler(new TopicNotFoundMessage(), this);
      service.addMessageHandler(new SubscriberNotFoundMessage(), this);
      service.addMessageHandler(new ServiceRemovedMessage(), this);
   }

   public Topic publish() {
      Topic topic = new Topic(this, topicCounter.incrementAndGet());
      topics.put(topic.topicId, topic);
      return topic;
   }

   public Topic publish(TopicFactory factory) {
      Topic topic = factory.newTopic(this, topicCounter.incrementAndGet());
      topics.put(topic.topicId, topic);
      return topic;
   }

   public void subscribe(int topicId, int entityId, int childId, boolean once) {
      final Topic topic = topics.get(topicId);
      if (topic == null) {
         logger.warn("subscribe: Could not find topic for {}", topicId);
      }
      topic.subscribe(entityId, childId, once);
   }

   public void unsubscribe(int topicId, int entityId, int childId, boolean all) {
      final Topic topic = topics.get(topicId);
      if (topic != null) {
         topic.unsubscribe(entityId, childId, all);
      } else {
         logger.warn("unsubscribe: Could not find topic for {} ", topicId);
      }
   }

   public int getEntityId() {
      return service.getEntityId();
   }

   public boolean sendMessage(Message msg, int toEntityId, int childId, int topicId) {
      return service.sendPrivateMessage(msg, toEntityId, childId);
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
         TaskContext taskContext = TaskContext.pushNew();
         try {
            ContextIdGenerator.setContextId(ctx.header.contextId);
            logger.info("@@@@@ UNSUBSCRIBING DISCONNECTED SUBSCRIBER {}", m.dump());
            unsubscribe(m.topicId, m.entityId, m.childId, m.all);
         } finally {
            taskContext.pop();
         }
      }
   }

   @Override
   public void messageTopicUnpublished(TopicUnpublishedMessage m, MessageContext ctx) {
      if (m.publisherId == service.getEntityId()) {
         logger.info("@@@@@ {} {}", m.dump(), ctx.header.dump());
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
      }
   }

   public void unsubscribeFromAllTopics(int entityId, int childId) {
      for (Topic t : topics.values()) {
         t.unsubscribe(entityId, childId, true);
      }
   }

   /**
    * Sent from a parent when it receives a broadcast for a topic it doesn't currently track
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
         unsubscribe(m.topicId, m.entityId, m.childId, true);
      }
   }

   /**
    * If a service is deleted, immediately clear all subscriptions it had.
    */
   @Override
   public void messageServiceRemoved(ServiceRemovedMessage m, MessageContext ctx) {
      for (Topic t : topics.values()) {
         t.clear(m.entityId);
      }
   }

}
