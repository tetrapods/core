package io.tetrapod.core;

import io.tetrapod.core.rpc.*;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread safe message dipatcher class.
 */
public class MessageHandlers implements SubscriptionAPI {
   
   private Queue<SubscriptionAPI> listeners = new ConcurrentLinkedQueue<>();

   @Override
   public void genericMessage(Message message) {
      for (SubscriptionAPI api : listeners)
         message.dispatch(api);
   }
   
   public void add(SubscriptionAPI listener) {
      listeners.add(listener);
   }
   
   public void remove(SubscriptionAPI listener) {
      listeners.remove(listener);
   }

}
