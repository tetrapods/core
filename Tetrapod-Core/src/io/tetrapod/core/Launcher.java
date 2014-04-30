package io.tetrapod.core;

import io.tetrapod.protocol.core.*;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

/**
 * Simple service launcher. Some day it might be nice to replace with a ClusterService that was able to launch things.
 * <p>
 * Don't refer to logging in this class otherwise it gets initialized (upon class load) prior to being setup.
 * <p>
 * Arguments:
 * <ul>
 * <li>-host hostname (host to connect to, overrides cluster.properties)
 * <li>-port portNum (port to connect to, overrides cluster.properties)
 * <li>-token token (reclaim token)
 * <li>-forceJoin true (forces a tetrapod to join a cluster even if it's connecting to localhost)
 * <li>-webOnly webRootName (service will connect, set web root, and disconnect)
 * </ul>
 */
public class Launcher {
   private static Map<String, String> opts         = null;
   private static String              serviceClass = null;

   public static void main(String[] args) {
      loadProperties("cfg/service.properties");
      loadClusterProperties();
      try {
         if (args.length < 1)
            usage();
         serviceClass = args[0];
         opts = getOpts(args, 1, defaultOpts());
         System.setProperty("APPNAME", serviceClass.substring(serviceClass.lastIndexOf('.') + 1));

         String host = System.getProperty("cluster.host"); 
         int port = Integer.parseInt(System.getProperty("cluster.port"));
         if (opts.get("host") != null) {
            host = opts.get("host");
         }
         if (opts.get("port") != null) {
            port = Integer.parseInt(opts.get("port"));
         }
         ServerAddress addr = new ServerAddress(host, port);
         Service service = (Service) getClass(serviceClass).newInstance();
         service.startNetwork(addr, opts.get("token"), opts);
      } catch (Throwable t) {
         t.printStackTrace();
         usage();
      }
   }

   private static Class<?> getClass(String serviceClass) {
      // actual class
      try {
         return tryName(serviceClass);
      } catch (ClassNotFoundException e) {}

      // capitalize first letter
      serviceClass = serviceClass.substring(0, 1).toUpperCase() + serviceClass.substring(1);
      
      // io.tetrapod.core.X
      try {
         return tryName("io.tetrapod.core." + serviceClass);
      } catch (ClassNotFoundException e) {}

      int ix = serviceClass.indexOf("Service");
      if (ix > 0) {
         // pop off Service if it's there
         serviceClass = serviceClass.substring(0, ix);
      }

      // io.tetrapod.core.XService
      try {
         return tryName("io.tetrapod.core." + serviceClass + "Service");
      } catch (ClassNotFoundException e) {}

      // io.tetrapod.lowercase(X).X
      try {
         return tryName("io.tetrapod." + serviceClass.toLowerCase() + "." + serviceClass);
      } catch (ClassNotFoundException e) {}

      // io.tetrapod.lowercase(X).XService
      try {
         return tryName("io.tetrapod." + serviceClass.toLowerCase() + "." + serviceClass + "Service");
      } catch (ClassNotFoundException e) {}

      return null;
   }
   
   private static Class<?> tryName(String name) throws ClassNotFoundException {
      System.out.println("trying " + name);
      return Class.forName(name);
   }

   private static void usage() {
      System.err.println("\nusage:\n\t java <vmopts> " + Launcher.class.getCanonicalName()
            + " serviceClass [-host hostname] [-port port] [-token authToken]\n");
      System.err
            .println("\nserviceClass can omit its prefix if it's io.tetrapod.{core|serviceClass.upTo(\"Service\").toLower}.serviceClass[Service]\n");
      System.exit(0);
   }

   private static Map<String, String> defaultOpts() {
      Map<String, String> map = new HashMap<>();
      map.put("host", null);
      map.put("port", null);
      map.put("token", null);
      return map;
   }

   private static Map<String, String> getOpts(String[] array, int startIx, Map<String, String> opts) {
      for (int i = startIx; i < array.length; i += 2) {
         String key = array[i];
         String value = array[i + 1];
         if (!key.startsWith("-")) {
            throw new RuntimeException("expected option, got [" + key + "]");
         }
         opts.put(key.substring(1), value);
      }
      return opts;
   }

   public static void relaunch(String token) throws IOException {
      opts.put("token", token);
      StringBuilder sb = new StringBuilder();
      sb.append("./scripts/launch");

      // java args?
      //      for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
      //         sb.append(' ');
      //         sb.append(arg);
      //      }

      for (Entry<String, String> entry : opts.entrySet()) {
         if (entry.getValue() != null) {
            sb.append(' ');
            sb.append('-');
            sb.append(entry.getKey());
            sb.append(' ');
            sb.append(entry.getValue());
         }
      }
      System.out.println("EXEC: " + sb);
      Runtime.getRuntime().exec(sb.toString());
   }
   
   public static boolean loadProperties(String fileName) {
      final File file = new File(fileName);
      if (file.exists()) {
         try (Reader reader = new FileReader(file)) {
            System.getProperties().load(reader);
            return true;
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
      return false;
   }
   
   private static void loadClusterProperties() {
      String[] locs = {
            "cluster.properties", // in prod, gets symlinked in
            "../../core/Tetrapod-Core/rsc/cluster/cluster.properties",
            "../../../core/Tetrapod-Core/rsc/cluster/cluster.properties", // in case services are one level deeper
      };
      for (String f : locs) {
         if (loadProperties(f))
            return;
      }
      System.err.println("ERR: no cluster properties found");
      System.exit(0);
   }

   public static String getOpt(String key) {
      return opts.get(key);
   }

}
