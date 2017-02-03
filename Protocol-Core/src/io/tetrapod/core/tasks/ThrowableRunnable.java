package io.tetrapod.core.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author paulm
 *         Created: 2/3/17
 */
public interface ThrowableRunnable {
   void run() throws Throwable;
}
