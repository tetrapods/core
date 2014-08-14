package io.tetrapod.core.utils;


import java.security.SecureRandom;
import java.util.Random;

public class Rand {
  
   private static Random r = new SecureRandom();
   
   public static int nextInt() {
      return r.nextInt();
   }

   public static int nextInt(int max) {
      return r.nextInt(max);
   }

   public static String nextBase36String(int numDigits) {
      StringBuilder sb = new StringBuilder();
      String digits = "0123456789abcdefghijklmnopqrstuvwxyz";
      for (int i=0; i<numDigits; i++) {
         sb.append(digits.charAt(nextInt(36)));
      }
      return sb.toString();
   }

   public static byte[] bytes(int len) {
      byte[] bytes = new byte[len];
      r.nextBytes(bytes);
      return bytes;
   }
}
