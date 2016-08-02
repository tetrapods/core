package io.tetrapod.core.tasks;

import java.util.function.BiFunction;

/**
 * Two arg function
 * @author paulm
 *         Created: 8/2/16
 */
@FunctionalInterface
public interface Func2<TRet, TParam0, TParam1> extends BiFunction<TRet, TParam0, TParam1> {

}
