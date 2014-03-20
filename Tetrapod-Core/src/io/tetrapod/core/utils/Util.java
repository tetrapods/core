package io.tetrapod.core.utils;

public class Util {

   public static void sleep(int millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {}
   }

}
