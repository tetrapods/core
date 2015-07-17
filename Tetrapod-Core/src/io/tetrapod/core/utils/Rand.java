package io.tetrapod.core.utils;

import java.security.SecureRandom;
import java.util.Random;

public class Rand {

   private final static Random r = new SecureRandom();

   public static int nextInt() {
      return r.nextInt();
   }

   public static int nextInt(int max) {
      return r.nextInt(max);
   }

   public static String nextBase36String(int numDigits) {
      StringBuilder sb = new StringBuilder();
      String digits = "0123456789abcdefghijklmnopqrstuvwxyz";
      for (int i = 0; i < numDigits; i++) {
         sb.append(digits.charAt(nextInt(36)));
      }
      return sb.toString();
   }

   public static String nextBase62String(int numDigits) {
      StringBuilder sb = new StringBuilder();
      String digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
      for (int i = 0; i < numDigits; i++) {
         sb.append(digits.charAt(nextInt(digits.length())));
      }
      return sb.toString();
   }

   public static byte[] bytes(int len) {
      byte[] bytes = new byte[len];
      nextBytes(bytes);
      return bytes;
   }

   public static long nextLong() {
      return r.nextLong();
   }

   public static boolean nextBoolean() {
      return r.nextBoolean();
   }

   public static double nextDouble() {
      return r.nextDouble();
   }

   public static float nextFloat() {
      return r.nextFloat();
   }

   public static double nextGaussian() {
      return r.nextGaussian();
   }

   public static void nextBytes(byte[] arr) {
      r.nextBytes(arr);
   }

}
