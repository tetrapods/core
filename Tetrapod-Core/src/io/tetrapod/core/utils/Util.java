package io.tetrapod.core.utils;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.net.ssl.*;
import javax.xml.bind.DatatypeConverter;

import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.tetrapod.core.ServiceException;
import io.tetrapod.core.json.*;
import io.tetrapod.core.rpc.ErrorResponseException;
import io.tetrapod.core.rpc.Flags_int;

/**
 * A random collection of useful static utility methods
 */
public class Util {

   public static final SecureRandom random        = new SecureRandom();

   public static final long         ONE_SECOND    = 1000;
   public static final long         ONE_MINUTE    = ONE_SECOND * 60;
   public static final long         ONE_HOUR      = ONE_MINUTE * 60;
   public static final long         ONE_DAY       = ONE_HOUR * 24;
   public static final long         ONE_WEEK      = ONE_DAY * 7;

   public static final int          MINS_IN_A_DAY = 24 * 60;

   public static final Properties   properties    = new Properties();

   /**
    * Sleeps the current thread for a number of milliseconds, ignores interrupts.
    */
   public static void sleep(long millis) {
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
    * Takes a keystore input stream and a keystore password and return an SSLContext for
    * TLS
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

   public static long[] toLongArray(Collection<Long> list) {
      long[] res = new long[list.size()];
      int i = 0;
      for (Iterator<Long> iterator = list.iterator(); iterator.hasNext();) {
         res[i++] = iterator.next();
      }
      return res;
   }

   public static boolean[] toBooleanArray(Collection<Boolean> list) {
      boolean[] res = new boolean[list.size()];
      int i = 0;
      for (Iterator<Boolean> iterator = list.iterator(); iterator.hasNext();) {
         res[i++] = iterator.next();
      }
      return res;
   }

   public static List<Integer> toList(int[] array) {
      List<Integer> list = new ArrayList<Integer>(array.length);
      for (int i = 0; i < array.length; i++) {
         list.add(array[i]);
      }
      return list;
   }

   public static Set<String> toSet(String[] array) {
      Set<String> set = new HashSet<String>(array.length);
      for (int i = 0; i < array.length; i++) {
         set.add(array[i]);
      }
      return set;
   }

   public static Set<Integer> toSet(int[] array) {
      Set<Integer> set = new HashSet<Integer>(array.length);
      for (int i = 0; i < array.length; i++) {
         set.add(array[i]);
      }
      return set;
   }

   public static byte[] readFile(File f) throws IOException {
      return Files.readAllBytes(f.toPath());
   }

   public static String readFileAsString(File f) throws IOException {
      return new String(readFile(f), Charset.forName("UTF-8"));
   }

   public static String getProperty(String key, String defaultValue) {
      synchronized (properties) {
         return properties.optString(key, defaultValue);
      }
   }

   public static String getProperty(String key) {
      synchronized (properties) {
         return properties.optString(key, null);
      }
   }

   public static void setProperty(String key, String val) {
      synchronized (properties) {
         properties.put(key, val);
      }
   }

   public static void clearProperty(String key) {
      synchronized (properties) {
         properties.remove(key);
      }
   }

   public static int getProperty(String key, int defaultValue) {
      final String val = getProperty(key);
      if (val == null) {
         return defaultValue;
      }
      return Integer.parseInt(val);
   }

   public static long getProperty(String key, long defaultValue) {
      final String val = getProperty(key);
      if (val == null) {
         return defaultValue;
      }
      return Long.parseLong(val);
   }

   public static boolean getProperty(String key, boolean defaultValue) {
      final String val = getProperty(key);
      if (val == null) {
         return defaultValue;
      }
      return Boolean.parseBoolean(val);
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

   public static boolean isLocal() {
      return Util.getProperty("devMode", "dev").equals("local");
   }

   public static boolean isDev() {
      return Util.getProperty("devMode", "dev").equals("dev");
   }

   public static boolean isProduction() {
      return Util.getProperty("devMode", "dev").equals("prod");
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

         // escape html
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
//         if (c == '/') {
//            sb.append("&#x2F;");
//            i++;
//            continue;
//         }

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

   public static Set<String> jsonArrayToStringSet(JSONArray array) {
      Set<String> result = new HashSet<>();
      if (array == null) {
         return result;
      }
      for (int i = 0; i < array.length(); i++) {
         result.add(array.getString(i));
      }
      return result;
   }

   public static Set<Integer> jsonArrayToIntegerSet(JSONArray array) {
      Set<Integer> result = new HashSet<>();
      if (array == null) {
         return result;
      }
      for (int i = 0; i < array.length(); i++) {
         result.add(array.getInt(i));
      }
      return result;
   }

   public static Set<Long> jsonArrayToLongSet(JSONArray array) {
      Set<Long> result = new HashSet<>();
      if (array == null) {
         return result;
      }
      for (int i = 0; i < array.length(); i++) {
         result.add(array.getLong(i));
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
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
         int r;
         while ((r = is.read()) != -1) {
            baos.write(r);
         }
         return new String(baos.toByteArray());
      } finally {
         is.close();
      }
   }

   public static String readURL(URL url) throws IOException {
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
         try (InputStream is = url.openStream()) {
            int r;
            while ((r = is.read()) != -1) {
               baos.write(r);
            }
            return new String(baos.toByteArray());
         }
      }
   }

   public static boolean isEmptyJs(String val) {
      return isEmpty(val) || "undefined".equals(val);
   }
   public static boolean isEmpty(String val) {
      return val == null || val.length() == 0;
   }

   public static String capitalizeFirst(String val) {
      if (isEmpty(val)) {
         return val;
      }
      return val.substring(0,1).toUpperCase() + val.substring(1);
   }


   public final static String ALPHANUMERIC_CHARS           = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
   public final static String ALPHANUMERIC_CHARS_UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

   public static String makeRandomAlphanumericStringUppercase(int len) {
      return makeRandomString(len, ALPHANUMERIC_CHARS_UPPERCASE);
   }

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

   public static String httpGet(String uri, String username, String password) throws IOException {
      final URL obj = new URL(uri);
      final String userPassword = username + ":" + password;
      final String encoding = AESEncryptor.encodeBase64(userPassword.getBytes());
      final URLConnection uc = obj.openConnection();
      uc.setRequestProperty("Authorization", "Basic " + encoding);
      uc.connect();
      try (InputStream is = uc.getInputStream()) {
         try (BufferedReader in = new BufferedReader(new InputStreamReader(is))) {
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
               response.append(inputLine);
            }
            return response.toString();
         }
      }
   }

   public static <T extends Object> boolean isEmpty(T[] array) {
      return array == null || array.length == 0;
   }

   public static boolean isEmpty(Collection<?> coll) {
      return coll == null ? true : coll.isEmpty();
   }

   public static boolean isEmpty(Map<?, ?> map) {
      return map == null ? true : map.isEmpty();
   }
   
   public static boolean isEmpty(JSONArray array) {
      return array == null ? true : array.length() == 0;
   }

   public static boolean equals(String a, String b) {
      return (a == null) ? (b == null) : a.equals(b);
   }

   /**
    * Checks if Object o has any of the bits of the flag set
    * 
    * @param o object to check, must have a "flags" field
    * @param flag to check for
    * @return true if that flag is set
    */
   public static boolean hasFlag(Object o, int flag) {
      if (o instanceof Flags_int) {
         return hasFlag(((Flags_int<?>) o).value, flag);
      }
      int source = 0;
      try {
         Field flagsField = o.getClass().getField("flags");
         source = (Integer) flagsField.get(o);
      } catch (Exception e) {
         e.printStackTrace();
      }
      return (source & flag) != 0;
   }

   /**
    * Checks if Object o has ALL of the bits of the flag set
    * 
    * @param o object to check, must have a "flags" field
    * @param flags to check for
    * @return true if that flag is set
    */
   public static boolean hasAllFlags(Object o, int flags) {
      if (o instanceof Flags_int) {
         return hasAllFlags(((Flags_int<?>) o).value, flags);
      }
      int source = 0;
      try {
         Field flagsField = o.getClass().getField("flags");
         source = (Integer) flagsField.get(o);
      } catch (Exception e) {
         e.printStackTrace();
      }
      return (source & flags) == flags;
   }

   /**
    * Checks if source has any of flag bits set
    */
   public static boolean hasFlag(int source, int flag) {
      return (source & flag) != 0;
   }

   /**
    * Checks if source has ALL of flag bits set
    */
   public static boolean hasAllFlags(int source, int flags) {
      return (source & flags) == flags;
   }

   /**
    * Download a URL's contents to the given file
    */
   public static void downloadFile(URL url, File toFile) throws IOException {
      try (InputStream in = new BufferedInputStream(url.openStream())) {
         try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(toFile))) {
            int n = 0;
            while (-1 != (n = in.read())) {
               out.write(n);
            }
         }
      }
   }

   /**
    * Launch a task in a new thread
    */
   public static void runThread(String name, Runnable runnable) {
      new Thread(runnable, name).start();
   }

   /**
    * Returns a md5 hash string for this file
    */
   public static String digest(String string) {
      try {
         MessageDigest md = MessageDigest.getInstance("MD5");
         return DatatypeConverter.printHexBinary(md.digest(string.getBytes("UTF-8"))).toUpperCase();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Returns a sha-256 hash string for this file
    */
   public static String sha256(String string) {
      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         return DatatypeConverter.printHexBinary(md.digest(string.getBytes("UTF-8"))).toUpperCase();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public static String camelCaseUnderscores(String str) {
      Matcher m = Pattern.compile("_([a-z])").matcher(str);
      StringBuffer sb = new StringBuffer();
      while (m.find()) {
         m.appendReplacement(sb, m.group().substring(1).toUpperCase());
      }
      m.appendTail(sb);
      return sb.toString();
   }

   public static String toString(Object object) {
      StringBuilder sb = new StringBuilder();
      sb.append(object.getClass().getSimpleName()).append(" { ");
      boolean first = true;
      for (Field f : object.getClass().getDeclaredFields()) {
         try {
            int mod = f.getModifiers();
            if (!Modifier.isStatic(mod)) {
               if (!Modifier.isPublic(mod)) {
                  f.setAccessible(true);
               }
               if (!first) {
                  sb.append(", ");
               }
               first = false;
               sb.append(f.getName()).append(":").append(f.get(object));
            }
         } catch (Exception e) {}
      }
      sb.append(" }");
      return sb.toString();
   }

   private final static SimpleDateFormat macaroonDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

   /**
    * Generate a timestamp in correct format for macaroon TTL checks
    */
   public static String macaroonTime(long millis) {
      synchronized (macaroonDateTimeFormat) {
         return macaroonDateTimeFormat.format(new Date(millis));
      }
   }

   /**
    * Query DNS server for a TXT record
    */
   public static String getTxtRecord(String domain) throws NamingException {
      return getTxtRecord(domain, false);
   }

   public static String getTxtRecord(String domain, boolean stripQuotes) throws NamingException {
      DirContext ctx = new InitialDirContext();
      Attributes attrs = ctx.getAttributes("dns:/" + domain, new String[] { "TXT" });
      Attribute attr = attrs.get("TXT");
      if (attr != null) {
         String value = attr.get().toString();
         if (value.length() > 1 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
         }
         return value;
      }
      return null;
   }

   /**
    * Return a comma separated list of the collection, with no outside delimiters. Empty
    * string if collection is null or empty.
    */
   public static String commaSeparated(Collection<?> coll) {
      if (isEmpty(coll))
         return "";
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      for (Object obj : coll) {
         if (first)
            first = false;
         else
            sb.append(',');
         sb.append(obj.toString());
      }
      return sb.toString();
   }

   @SuppressWarnings("unchecked")
   public static <T> T cast(Object obj) {
      return (T) obj;
   }

   public static boolean isEqual(Object a, Object b) {
      if (a == b) {
         return true;
      } else if ((a == null && b != null) || a != null && b == null) {
         return false;
      } else {
         return a.equals(b);
      }
   }

   public static Throwable getRootCause(Throwable ex) {
      while (ex.getCause() != null && ex != ex.getCause()) {
         ex = ex.getCause();
      }
      return ex;
   }


   /**
    * Given a throwable, this will find if there is a throwable that is descendant from the specified class.
    * @param t The throwable to check
    * @param throwableClass The throwable class to check for
    * @param <T> The throwable type to search for
    * @return true if found in chain, false if not
    */
   public static <T extends Throwable> boolean isThrowableInChain(Throwable t, Class<T> throwableClass) {
      return getThrowableInChain(t, throwableClass) != null;
   }

   /**
    * Given a throwable, this will find if there is a throwable that is descendant from the specified class or null if not found.
    * @param t The throwable to check
    * @param throwableClass The throwable class to check for
    * @param <T> The throwable type to search for
    * @return The throwable that descneds T, or null if it's not in the chain
    */
   public static <T extends Throwable> T getThrowableInChain(Throwable t, Class<T> throwableClass) {
      return CoreUtil.getThrowableInChain(t, throwableClass);
   }

   /**
    * Given a throwable, if it contains an ErrorResponseException in its cause chain, and that code matches one of the codes
    * specified, it will return true.  Otherwise it will rethorw the exception, wrapping it as an unchecked exception if necessary.
    * @param t The throwable to check
    * @param errorCodes One or more error cods to check for
    * @return returns true if the error code was found, or re-throws the original exception if not.
    * @throws RuntimeException  The function can throw a runtime exception if it doesn't match the error code specified
    */
   public static boolean hasErrorCodeOrThrow(Throwable t, int ... errorCodes) {
      return hasErrorCode(true, t, errorCodes);
   }

   /**
    * Given a throwable, if it contains an ErrorResponseException in its cause chain, and that code matches one of the codes
    * specified, it will return true.  Otherwise it will return false;
    * @param t The throwable to check
    * @param errorCodes One or more error cods to check for
    * @return returns true if the error code was found, false if not
    */
   public static boolean hasErrorCode(Throwable t, int ... errorCodes) {
      return hasErrorCode(false, t, errorCodes);
   }

   /**
    * Given a throwable, if it contains an ErrorResponseException in its cause chain, return the errorCode.  Otherwise it will return null;
    * @param t The throwable to check
    * @return returns int error code if found otherwise null
    */
   public static Integer getErrorCode(Throwable t) {
      ErrorResponseException ex = getThrowableInChain(t, ErrorResponseException.class);
      if (ex != null) {
         return ex.errorCode;
      }
      return null;
   }

   private static boolean hasErrorCode(boolean rethrowIFNotFound, Throwable t, int ... errorCodes) {
      if (errorCodes.length == 0) {
         throw new IllegalArgumentException("You must specify at least one error code");
      }
      Integer errorCode = getErrorCode(t);
      if (errorCode!= null) {
         for (int ec : errorCodes) {
            if (ec == errorCode) {
               return true;
            }
         }
      }
      if (rethrowIFNotFound) {
         throw ServiceException.wrapIfChecked(t);
      }
      return false;
   }

   @SuppressWarnings("deprecation")
   public static List<Integer> getAccountIdsFromCookiesAndParams(String headers, String params) {
      List<String> auths = new ArrayList<>();
      if (params != null) {
         JSONObject paramObj = new JSONObject(params);
         String auth = paramObj.optString("auth", null);
         if (auth != null) {
            auths.add(auth);
         }
      }

      if (headers !=null) {
         JSONObject jo = new JSONObject(headers);
         String cookie = jo.optString("Cookie", "");
         Set<Cookie> cookies = CookieDecoder.decode(cookie);
         for (Cookie c : cookies) {
            if (c.getName().equals("auth") || c.getName().equals("zdauth")) {
               try {
                  String auth = URLDecoder.decode(c.getValue(), "UTF-8");
                  auth = auth.substring(auth.lastIndexOf(';') + 1);
                  auths.add(auth);
               } catch (UnsupportedEncodingException e) {}
            }
         }
      }


      ArrayList<Integer> ids = new ArrayList<>();
      for (String authToken : auths) {
         LoginAuthToken.DecodedLogin decode = LoginAuthToken.decodeLoginToken(authToken);
         if (decode != null && decode.timeLeft > 0) {
            ids.add(decode.accountId);
         }
      }
      return ids;
   }

   public static <T> T getField(Object object, String fieldName) {
      try {
         Field field = object.getClass().getDeclaredField(fieldName);
         field.setAccessible(true);
         return cast(field.get(object));
      } catch (Throwable e) {
         throw ServiceException.wrapIfChecked(e);
      }
   }


   public interface ValueMaker<K, V> {
      public V make();
   }

   /**
    * Helpful method to get an existing value from a map or lazy-init when value is
    * missing.
    */
   public static <K, V> V getOrMake(Map<K, V> map, K key, ValueMaker<K, V> maker) {
      V val = map.get(key);
      if (val == null) {
         val = maker.make();
         map.put(key, val);
      }
      return val;
   }

   /**
    * Helpful method to get an existing value from a map or lazy-init when value is
    * missing.
    */
   public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultVal) {
      V val = map.get(key);
      if (val == null) {
         val = defaultVal;
         map.put(key, val);
      }
      return val;
   }

   /**
    * Return a new array made by appending the given value. The source array can be null
    * as well which results in an array containing only the supplied value.
    */
   @SuppressWarnings("unchecked")
   public static <T> T[] append(T[] array, T value) {
      T[] res;
      if (array == null) {
         res = (T[]) Array.newInstance(value.getClass(), 1);
      } else {
         res = Arrays.copyOf(array, array.length + 1);
      }
      res[res.length - 1] = value;
      return res;
   }

   public static String formatDollars(int pennies) {
      return String.format("$%1.2f", pennies / 100.0);
   }

   public static String formatCents(double pennies) {
      return String.format("%1.1f¢", pennies);
   }

   public static List<String> getResourceFiles(Class<?> context, String path) throws IOException {
      List<String> filenames = new ArrayList<>();
      try (InputStream in = context.getResourceAsStream(path); BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
         String resource;
         while ((resource = br.readLine()) != null) {
            filenames.add(resource);
         }
      }

      return filenames;
   }

}
