package io.tetrapod.core;

public class Util {

   public static void sleep(int millis) {
      try {
         Thread.sleep(1000);
      } catch (InterruptedException e) {}
   }

}
