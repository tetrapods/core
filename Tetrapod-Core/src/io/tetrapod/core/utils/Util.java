package io.tetrapod.core.utils;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.*;

import javax.net.ssl.*;

/**
 * A random collection of useful static utility methods
 */
public class Util {

   /**
    * Sleeps the current thread for a number of milliseconds, ignores interrupts.
    */
   public static void sleep(int millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {}
   }

   /**
    * Get the hostname for this system
    */
   public static String getHostName() {
      try {
         return InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException e) {}
      return null;
   }

   /**
    * Takes a keystore input stream and a keystore password and return an SSLContext for TLS
    */
   public static SSLContext createSSLContext(InputStream keystoreStream, char[] pwd) throws IOException {
      try {
         final KeyStore trustStore = KeyStore.getInstance("JKS");
         trustStore.load(keystoreStream, pwd);
         final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
         trustManagerFactory.init(trustStore);
         final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
         keyManagerFactory.init(trustStore, pwd);
         final SSLContext ctx = SSLContext.getInstance("TLS");
         ctx.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
         return ctx;
      } catch (Exception e) {
         throw new IOException(e);
      }
   }

   public static int random(int range) {
      return (int) (Math.random() * range);
   }

   public static String format(String fmt, Object... args) {
      return String.format(fmt.replaceAll("\\{\\}", "%s"), args);
   }

   public static long nanosToMillis(long nanos) {
      return nanos / 1000000L;
   }

   public static <T> T random(final Collection<T> items) {
      return random(new ArrayList<T>(items));
   }

   public static <T> T random(final List<T> items) {
      return items.get(random(items.size()));
   }

   @SafeVarargs
   public static <T> T randomChoice(final T... items) {
      return items[random(items.length)];
   }

   public static int[] toIntArray(List<Integer> list) {
      int[] res = new int[list.size()];
      int i = 0;
      for (Iterator<Integer> iterator = list.iterator(); iterator.hasNext();) {
         res[i++] = iterator.next();
      }
      return res;
   }

   public static long[] toLongArray(List<Long> list) {
      long[] res = new long[list.size()];
      int i = 0;
      for (Iterator<Long> iterator = list.iterator(); iterator.hasNext();) {
         res[i++] = iterator.next();
      }
      return res;
   }

   public static boolean[] toBooleanArray(List<Boolean> list) {
      boolean[] res = new boolean[list.size()];
      int i = 0;
      for (Iterator<Boolean> iterator = list.iterator(); iterator.hasNext();) {
         res[i++] = iterator.next();
      }
      return res;
   }

   public static byte[] readFile(File f) throws IOException {
      return Files.readAllBytes(f.toPath());
   }

   public static String readFileAsString(File f) throws IOException {
      return new String(readFile(f), Charset.forName("UTF-8"));
   }

   public static int getProperty(String key, int defaultValue) {
      final String val = System.getProperty(key);
      if (val == null) {
         return defaultValue;
      }
      return Integer.parseInt(val);
   }
   
   public static String getProperty(String key, String defaultValue) {
      return System.getProperty(key, defaultValue);
   }
   
   public static String getProperty(String key) {
      return System.getProperty(key);
   }

   public static int runProcess(Callback<String> callback, String... commands) {
      ProcessBuilder pb = new ProcessBuilder(commands);
      pb.redirectErrorStream(true);
      try {
         Process p = pb.start();
         String s;
         BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
         while ((s = stdout.readLine()) != null) {
            if (callback != null)
               callback.call(s);
         }
         int rc = p.waitFor();
         p.getInputStream().close();
         p.getOutputStream().close();
         p.getErrorStream().close();
         return rc;
      } catch (Exception ex) {
         return -1;
      }
   }

   public static boolean getProperty(String key, boolean defaultValue) {
      final String val = System.getProperty(key);
      if (val == null) {
         return defaultValue;
      }
      return Boolean.parseBoolean(val);
   }
   
   public static boolean isLocal() {
      return Util.getProperty("devMode", "local").equals("local");
   }

}
