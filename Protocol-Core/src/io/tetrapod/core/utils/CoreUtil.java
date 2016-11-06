package io.tetrapod.core.utils;

/**
 * @author paulm
 *         Created: 8/29/16
 */
public class CoreUtil {

   /**
    * Given several arguments, it will return the first one that is not null
    * @param objects  Objects to test for null
    * @param <T>  The type that all the objects must conform to
    * @return  The first non null object, or null of they are all null
    */
   public static <T> T coalesce(T ... objects) {
      for (T obj : objects) {
         if (obj != null) {
            return obj;
         }
      }
      return null;
   }

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
