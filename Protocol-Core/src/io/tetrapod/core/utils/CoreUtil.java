package io.tetrapod.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author paulm
 *         Created: 8/29/16
 */
public class CoreUtil {



   public static <T extends Throwable> T getThrowableInChain(Throwable t, Class<T> throwableClass) {
      do {
         if (throwableClass.isAssignableFrom(t.getClass())) {
            return cast(t);
         }
         if (t.getCause() == null || t.getCause() == t) {
            return null;
         }
         t = t.getCause();
      } while (true);
   }


   @SuppressWarnings("unchecked")
   public static <T> T cast(Object obj) {
      return (T) obj;
   }
}
