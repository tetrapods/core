package io.tetrapod.core.rpc;

import java.lang.reflect.Field;
import java.util.*;

public class ErrorChecker {

   private static final Map<Class<?>,Set<Integer>> map = new HashMap<>();
   
   public static boolean validateError(Request request, int errorCode) {
      // always allow core erros
      if (errorCode < 100)
         return true;
      Set<Integer> errs = null;;
      synchronized (ErrorChecker.class) {
         errs = map.get(request.getClass());
      }
      if (errs == null) {
         errs = fillErrors(request);
         synchronized (ErrorChecker.class) {
            map.put(request.getClass(), errs);
         }
      }
      return errs.contains(errorCode);
   }
   
   private static Set<Integer> fillErrors(Request request) {
      HashSet<Integer> set = new HashSet<>();
      for (Field f : request.getClass().getDeclaredFields()) {
         if (f.isAnnotationPresent(ERR.class)) {
            try {
               int e = f.getInt(request);
               set.add(e);
            } catch (IllegalArgumentException | IllegalAccessException e1) {
            }
         }
      }
      return set;
   }
   
}
