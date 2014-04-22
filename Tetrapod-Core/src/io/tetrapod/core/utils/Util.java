package io.tetrapod.core.utils;

import java.io.*;
import java.net.*;
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

   public static byte[] readFile(File f) throws IOException {
      try (FileInputStream in = new FileInputStream(f)) {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         int b = in.read();
         while (b >= 0) {
            out.write(b);
            b = in.read();
         }
         return out.toByteArray();
      }
   }

}
