package io.tetrapod.core.tasks;

/**
 * Three arg function
 * @author paulm
 *         Created: 8/2/16
 */
@FunctionalInterface
public interface Func3<TRet, TParam0, TParam1, TParam2> {
   TRet apply(TParam0 p0, TParam1 p1, TParam2 p2);
}