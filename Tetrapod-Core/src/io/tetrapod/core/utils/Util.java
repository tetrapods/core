package io.tetrapod.core.utils;

import io.tetrapod.core.json.*;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.*;
import java.util.*;

import javax.net.ssl.*;

/**
 * A random collection of useful static utility methods
 */
public class Util {

   public static final SecureRandom random     = new SecureRandom();

   public static long               ONE_SECOND = 1000;
   public static long               ONE_MINUTE = ONE_SECOND * 60;
   public static long               ONE_HOUR   = ONE_MINUTE * 60;
   public static long               ONE_DAY    = ONE_HOUR * 24;
   public static long               ONE_WEEK   = ONE_DAY * 7;

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
      return random.nextInt(range);
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

   public static int[] toIntArray(Collection<Integer> list) {
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

   public static boolean isDev() {
      return Util.getProperty("devMode", "dev").equals("dev");
   }

   public static boolean isProduction() {
      return Util.getProperty("devMode", "local").equals("prod");
   }

   public static String formatFileSize(long bytes) {
      if (bytes < 4096) {
         return bytes + " bytes";
      } else if (bytes < 1024 * 1024) {
         return (Math.round(10 * bytes / 1024) / 10) + " kb";
      } else {
         return (Math.round(10 * bytes / (1024 * 1024)) / 10) + " mb";
      }
   }

   public static String escapeHTML(String text) {
      if (text == null) {
         return "";
      }
      StringBuilder sb = new StringBuilder();
      char[] chars = text.toCharArray();
      int i = 0;
      int n = chars.length;
      while (i < n) {
         char c = chars[i];

         // escape html < and > and &
         if (c == '<') {
            sb.append("&lt;");
            i++;
            continue;
         }
         if (c == '>') {
            sb.append("&gt;");
            i++;
            continue;
         }
         if (c == '&') {
            sb.append("&amp;");
            i++;
            continue;
         }
         if (c == '"') {
            sb.append("&quot;");
            i++;
            continue;
         }
         if (c == '\'') {
            sb.append("&#x27;");
            i++;
            continue;
         }

         i++;
         sb.append(c);
      }
      return sb.toString();
   }

   public static List<String> jsonArrayToStringList(JSONArray array) {
      List<String> result = new ArrayList<>();
      if (array == null) {
         return result;
      }
      for (int i = 0; i < array.length(); i++) {
         result.add(array.getString(i));
      }
      return result;
   }

   public static List<Integer> jsonArrayToIntegerList(JSONArray array) {
      List<Integer> result = new ArrayList<>();
      if (array == null) {
         return result;
      }
      for (int i = 0; i < array.length(); i++) {
         result.add(array.getInt(i));
      }
      return result;
   }

   public static int indexOf(int[] array, int element) {
      for (int i = 0; i < array.length; i++) {
         if (array[i] == element)
            return i;
      }
      return -1;
   }

   public static String readStream(InputStream is) throws IOException {
      String str = null;
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try {
         int r;
         while ((r = is.read()) != -1) {
            baos.write(r);
         }
         str = new String(baos.toByteArray());
      } finally {
         baos.close();
         is.close();
      }
      return str;
   }

   public static boolean isEmpty(String val) {
      return val == null || val.length() == 0;
   }

   public final static String ALPHANUMERIC_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

   public static String makeRandomAlphanumericString(int len) {
      return makeRandomString(len, ALPHANUMERIC_CHARS);
   }

   public static String makeRandomString(int len, String chars) {
      final StringBuilder sb = new StringBuilder();
      while (--len >= 0) {
         sb.append(chars.charAt(Util.random(chars.length())));
      }
      return sb.toString();
   }

   public static JSONObject httpPost(String uri, String data, JSONObject headers) throws IOException {
      URL obj = new URL(uri);
      HttpURLConnection con = (HttpURLConnection) obj.openConnection();
      con.setRequestMethod("POST");
      con.setRequestProperty("Content-Length", String.valueOf(data.length()));
      if (headers == null)
         headers = new JSONObject();
      if (!headers.has("Accept-Charset"))
         con.setRequestProperty("Accept-Charset", "UTF-8");
      if (!headers.has("Charset"))
         con.setRequestProperty("Charset", "UTF-8");
      if (!headers.has("Content-Type"))
         con.setRequestProperty("Content-Type", "application/json");
      for (Object k : headers.keySet()) {
         con.setRequestProperty(k.toString(), headers.optString(k.toString()));
      }

      con.setDoOutput(true);
      OutputStream wr = con.getOutputStream();
      wr.write(data.getBytes("UTF-8"));
      wr.flush();
      wr.close();

      int responseCode = con.getResponseCode();

      InputStream is = responseCode == 200 ? con.getInputStream() : con.getErrorStream();
      BufferedReader in = new BufferedReader(new InputStreamReader(is));
      String inputLine;
      StringBuilder response = new StringBuilder();
      while ((inputLine = in.readLine()) != null) {
         response.append(inputLine);
      }
      in.close();

      return new JSONObject().put("status", responseCode).put("body", response.toString());
   }

}
