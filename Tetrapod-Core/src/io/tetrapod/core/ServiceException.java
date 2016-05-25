package io.tetrapod.core;

import io.tetrapod.core.utils.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * This class is designed to take exceptions (checked or otherwise) and turn them into unchecked exceptions.  Throwing
 * this exception within a DefaultService invocation will result in this being caught, logged and turned into an UNKNOWN_ERROR
 *
 * @author paulm
 *         Created: 5/23/16
 */
public class ServiceException extends RuntimeException {

   /**
    * Takes a throwable and wraps it in a ServiceException, insuring its not doing a double wrapping.
    * @param throwable  The exception to wrap
    * @return  The ServiceException to throw
    */
   public static ServiceException wrap(Throwable throwable) {
      if (throwable instanceof ServiceException) {
         return Util.cast(throwable);
      } else {
         return new ServiceException(throwable);
      }
   }

   public static <T> T run(ThrowableSupplier<T> supplier) {
      try {
         return supplier.get();
      } catch (Throwable t) {
         throw wrap(t);
      }
   }

   public interface ThrowableSupplier<T> {
      T get() throws Throwable;
   }

   public ServiceException(String message) {
      super(message);
   }

   public ServiceException(String message, Throwable cause) {
      super(message, cause);
   }

   public ServiceException(Throwable cause) {
      super(cause);
   }

   /**
    * Returns the root cause of this exception
    * @return The root cause
    */
   public Throwable rootCause() {
      Throwable cause = this;
      while (cause.getCause() != null) {
         cause = cause.getCause();
      }
      return cause;
   }
}
