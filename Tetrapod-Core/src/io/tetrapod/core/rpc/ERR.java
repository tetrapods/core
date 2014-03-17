package io.tetrapod.core.rpc;

import java.lang.annotation.*;

/**
 * Marker annotation for error codes in code generated file.  At runtime we can optionally
 * check return codes from the request to make sure it is a declared error.
 * 
 * @author fortin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD) 
public @interface ERR {

}
