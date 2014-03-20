package io.tetrapod.core;

import java.util.*;

/**
 * Simple service launcher.  Some day it might be nice to replace with a ClusterService that was
 * able to launch things.
 */
public class Launcher {

   public static void main(String[] args) {
      try {
         if (args.length < 1)
            usage();
         String serviceClass = args[0];
         Map<String,String> opts = getOpts(args, 1, defaultOpts());
         Service service = (Service)Class.forName(serviceClass).newInstance();
         service.startNetwork(opts.get("host"), opts.get("token"));
      } catch (Throwable t) {
         t.printStackTrace();
         usage();
      }
   }
   
   // launch 30m io.tetrapods.identity.IdentityService -join 192.160.0.66:33456 -token sdkjfrinbnriurtdjvdknmnnlkjrii
   
   private static void usage() {
      System.err.println("\nusage: java <vmopts> " + Launcher.class.getCanonicalName() + " serviceClassName [-join hostname[:port]] [-token authToken]\n");
      System.exit(0);
   }
   
   private static Map<String,String> defaultOpts() {
      Map<String, String> map = new HashMap<>();
      map.put("join", null);
      map.put("token", null);
      return map;
   }
   
   private static Map<String,String> getOpts(String[] array, int startIx, Map<String,String> opts) {
      for (int i = startIx; i < array.length; i += 2) {
         String key = array[i];
         String value = array[i+1];
         if (!key.startsWith("-")) {
            throw new RuntimeException("expected option, got [" + key + "]");
         }
         opts.put(key.substring(1), value);
      }
      return opts;
   }
   
   
   
}
