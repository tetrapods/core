package io.tetrapod.core.pubsub;

public interface TopicFactory {
   Topic newTopic(Publisher publisher, int topicId);
}
