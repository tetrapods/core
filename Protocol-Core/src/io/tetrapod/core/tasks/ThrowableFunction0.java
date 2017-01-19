package io.tetrapod.core.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author paulm
 *         Created: 12/28/16
 */
@FunctionalInterface
public interface ThrowableFunction0<R> {
   R apply() throws Throwable;
}

