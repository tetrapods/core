package io.tetrapod.core.registry;

import io.tetrapod.core.protocol.Entity;

import java.util.Map;

/**
 * All the meta data associated with a tetrapod entity
 */
public class EntityInfo extends Entity {

   // TODO: stats
 
   private Map<Integer, Topic> topics;
   private Map<Integer, Topic> subscriptions;

}
