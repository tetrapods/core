package io.tetrapod.core.pubsub;

import java.util.HashSet;
import java.util.Set;

import io.tetrapod.core.DefaultService;
import io.tetrapod.core.rpc.Message;

/**
 * Wrapper for Topic we're publishing in a service
 */
public class Topic {

   public static DefaultService service;

   public final int             topicId;

   public Set<Integer>          subscribers = new HashSet<>();

   public Topic(int topicId) {
      this.topicId = topicId;
   }

   public void broadcast(Message msg) {
      service.sendBroadcastMessage(msg, topicId);
   }

   public int numSubscribers() {
      return subscribers.size();
   }

   public void subscribe(int entityId, boolean bool) {
      subscribers.add(entityId);
      service.subscribe(topicId, entityId);
   }

   public void unsubscribe(int entityId) {
      subscribers.remove(entityId);
      service.unsubscribe(topicId, entityId);
   }

   public void unpublish() {
      service.unpublish(topicId);
   }

}
