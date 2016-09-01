package io.tetrapod.core.tasks;

import java.util.function.Function;

/**
 * One arg function
 * @author paulm
 *         Created: 8/2/16
 */
@FunctionalInterface
public interface Func1<TRet, TParam0> extends Function<TParam0, TRet> {
}
