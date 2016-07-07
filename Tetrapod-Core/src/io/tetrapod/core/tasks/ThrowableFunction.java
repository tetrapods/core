package io.tetrapod.core.tasks;

/**
 * @author paulm
 *         Created: 6/20/16
 */
@FunctionalInterface
public interface ThrowableFunction<T, R> {
   R apply(T t) throws Throwable;
}
