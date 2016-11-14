package io.tetrapod.core.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author paulm
 *         Created: 10/19/16
 */
public interface SubscriptionAPITyped<T extends Message> {
   void genericMessage(T message, MessageContext ctx);
}
