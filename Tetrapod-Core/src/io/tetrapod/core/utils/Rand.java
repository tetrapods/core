package io.tetrapod.core.utils;


import java.security.SecureRandom;
import java.util.Random;

public class Rand {
  
   private static Random r = new SecureRandom();
   
   public static int nextInt() {
      return r.nextInt();
   }
}
