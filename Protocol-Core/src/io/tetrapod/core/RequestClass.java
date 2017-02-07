package io.tetrapod.core;

import io.tetrapod.core.rpc.Request;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author paulm
 *         Created: 2/3/17
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestClass {
   Class<? extends Request> value();
}
