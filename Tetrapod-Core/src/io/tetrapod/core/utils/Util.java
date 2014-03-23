package io.tetrapod.core.utils;

import java.net.*;

public class Util {

   public static void sleep(int millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {}
   }

   public static String getHostName() {
      try {
         return InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException e) {}
      return null;
   }

}
