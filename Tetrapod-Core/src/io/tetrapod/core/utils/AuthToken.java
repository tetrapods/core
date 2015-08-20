package io.tetrapod.core.utils;

import io.netty.buffer.*;
import io.netty.handler.codec.base64.*;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Helper class used to authenticate messages. Works by taking a message (an array of ints) and computing an HMAC using a shared secret. The
 * token is then a subset(input vals) + HMAC. Upon decoding the subset of input vals are recovered, combined with any values known through
 * other means, and the HMAC is recomputed and checked for validity.
 * <p>
 * Note that the actual data encoded and decoded in the token is up to the caller.
 */
public class AuthToken {

   private static final long NOT_THAT_LONG_AGO = 1395443029600L;
   private static Mac        MAC_SECURE        = null;
   private static Mac        MAC_GATES         = null;
   private static Mac        MAC_ADMIN         = null;
   private static String     SECURED_EXTRA     = "HkRzUvnYA5laYVkCHyFNViqLiP2GlzdMLAJnQZKqCMBQhZEhCZ"; // change if we have a breach
   private static String     ADMIN_EXTRA       = "9TNnbodlByJPeRJuznUyel2whHoAMIKmuViLwePlOIbWuoZrkB"; // change if we have a breach

   /**
    * Sets the shared secret. Needs to be called before this class is used. Returns false if there is an error which would typically be Java
    * not having strong crypto available.
    */
   public static boolean setSecret(byte[] secret1) {
      try {
         byte[] secret2 = Arrays.copyOfRange(secret1, 1, secret1.length - 1);
         byte[] secret3 = Arrays.copyOfRange(secret2, 1, secret2.length - 1);
         // append NTLA so updating it will invalidate old tokens
         byte[] ntla = Long.toHexString(NOT_THAT_LONG_AGO).getBytes();
         byte[] secureExtra = SECURED_EXTRA.getBytes();
         byte[] key = new byte[secret2.length + ntla.length + secureExtra.length];
         System.arraycopy(ntla, 0, key, 0, ntla.length);
         System.arraycopy(secret2, 0, key, ntla.length, secret2.length);
         System.arraycopy(secureExtra, 0, key, ntla.length + secret2.length, secureExtra.length);
         SecretKeySpec signingKey = new SecretKeySpec(key, "HmacSHA1");
         Mac macCoder = Mac.getInstance("HmacSHA1");
         macCoder.init(signingKey);
         synchronized (AuthToken.class) {
            MAC_SECURE = macCoder;
            setSecretGates(secret1); // give gates original so old tokens work
            setSecretAdmin(secret3);
         }
         return true;
      } catch (Exception e) {
         return false;
      }
   }
   
   /**
    * Sets the shared secret. Needs to be called before this class is used. Returns false if there is an error which would typically be Java
    * not having strong crypto available.
    */
   public static boolean setSecretAdmin(byte[] secret) {
      try {
         // append NTLA so updating it will invalidate old tokens
         byte[] ntla = Long.toHexString(NOT_THAT_LONG_AGO).getBytes();
         byte[] secureExtra = ADMIN_EXTRA.getBytes();
         byte[] key = new byte[secret.length + ntla.length + secureExtra.length];
         System.arraycopy(ntla, 0, key, 0, ntla.length);
         System.arraycopy(secret, 0, key, ntla.length, secret.length);
         System.arraycopy(secureExtra, 0, key, ntla.length + secret.length, secureExtra.length);
         SecretKeySpec signingKey = new SecretKeySpec(key, "HmacSHA1");
         Mac macCoder = Mac.getInstance("HmacSHA1");
         macCoder.init(signingKey);
         synchronized (AuthToken.class) {
            MAC_ADMIN = macCoder;
         }
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   /**
    * Sets the shared secret. Needs to be called before this class is used. Returns false if there is an error which would typically be Java
    * not having strong crypto available.
    */
   public static boolean setSecretGates(byte[] secret) {
      try {
         // append NTLA so updating it will invalidate old tokens
         byte[] ntla = Long.toHexString(NOT_THAT_LONG_AGO).getBytes();
         byte[] secureExtra = SECURED_EXTRA.getBytes();
         byte[] key = new byte[secret.length + ntla.length + secureExtra.length];
         System.arraycopy(ntla, 0, key, 0, ntla.length);
         System.arraycopy(secret, 0, key, ntla.length, secret.length);
         System.arraycopy(secureExtra, 0, key, ntla.length + secret.length, secureExtra.length);
         SecretKeySpec signingKey = new SecretKeySpec(key, "HmacSHA1");
         Mac macCoder = Mac.getInstance("HmacSHA1");
         macCoder.init(signingKey);
         synchronized (AuthToken.class) {
            MAC_SECURE = macCoder;
            setSecretGates(Arrays.copyOfRange(secret, 1, secret.length-2));
            setSecretAdmin(Arrays.copyOfRange(secret, 1, secret.length-2));
         }
         return true;
      } catch (Exception e) {
         return false;
      }
   }
   
   /**
    * Sets the shared secret. Needs to be called before this class is used. Returns false if there is an error which would typically be Java
    * not having strong crypto available.
    */
   public static boolean setSecretAdmin(byte[] secret) {
      try {
         // append NTLA so updating it will invalidate old tokens
         byte[] ntla = Long.toHexString(NOT_THAT_LONG_AGO).getBytes();
         byte[] secureExtra = ADMIN_EXTRA.getBytes();
         byte[] key = new byte[secret.length + ntla.length + secureExtra.length];
         System.arraycopy(ntla, 0, key, 0, ntla.length);
         System.arraycopy(secret, 0, key, ntla.length, secret.length);
         System.arraycopy(secureExtra, 0, key, ntla.length + secret.length, secureExtra.length);
         SecretKeySpec signingKey = new SecretKeySpec(key, "HmacSHA1");
         Mac macCoder = Mac.getInstance("HmacSHA1");
         macCoder.init(signingKey);
         synchronized (AuthToken.class) {
            MAC_ADMIN = macCoder;
         }
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   /**
    * Sets the shared secret. Needs to be called before this class is used. Returns false if there is an error which would typically be Java
    * not having strong crypto available.
    */
   public static boolean setSecretGates(byte[] secret) {
      try {
         // append NTLA so updating it will invalidate old tokens
         byte[] ntla = Long.toHexString(NOT_THAT_LONG_AGO).getBytes();
         byte[] key = Arrays.copyOf(secret, secret.length + ntla.length);
         System.arraycopy(ntla, 0, key, secret.length, ntla.length);
         SecretKeySpec signingKey = new SecretKeySpec(key, "HmacSHA1");
         Mac macCoder = Mac.getInstance("HmacSHA1");
         macCoder.init(signingKey);
         synchronized (AuthToken.class) {
            MAC_GATES = macCoder;
         }
         return true;
      } catch (Exception e) {
         return false;
      }
   }


   /**
    * Encodes a auth token with all passed in values also present in the auth token.
    * 
    * @param values the values which form the basis of the token
    * @return the base64 encoded token
    */
   public static String encode(Mac theMac, int... values) {
      return encode(theMac, values, values.length);
   }

   /**
    * Encodes a auth token. The numInToken parameter is a bandwidth micro-optimization. Say for example we wanted to make an auth token with
    * one value being the entityId. We *could* encode the true value for the entity id in the token, but since it is already present in the
    * header we could leave it out of the token and supply it at decode time.
    * 
    * @param values the values which form the basis of the token
    * @param numInToken the number of values which will need to be encoded inside the token
    * @return the base64 encoded token
    */
   public static String encode(Mac theMAC, int[] values, int numInToken) {
      ByteBuf buf = Unpooled.buffer();
      try {
         ByteBufDataSource bds = new ByteBufDataSource(buf);
         for (int i = 0; i < values.length; i++) {
            bds.writeVarInt(values[i]);
         }
         byte[] mac;
         synchronized (AuthToken.class) {
            theMAC.update(buf.array(), buf.arrayOffset(), buf.writerIndex());
            mac = theMAC.doFinal();
         }
         buf.resetWriterIndex();
         for (int i = 0; i < numInToken; i++) {
            bds.writeVarInt(values[i]);
         }
         String plainText = Base64.encode(buf, Base64Dialect.URL_SAFE).toString(Charset.forName("UTF-8"));
         ByteBuf macBuf = Unpooled.wrappedBuffer(mac);
         try {
            String macText = Base64.encode(macBuf, Base64Dialect.URL_SAFE).toString(Charset.forName("UTF-8"));
            return plainText + macText;
         } finally {
            macBuf.release();
         }
      } catch (IOException e) {

      } finally {
         buf.release();
      }
      return null;
   }

   /**
    * Decodes an auth token. If there is at least one value in the token it assumes the first value is a timeout value and checks it versus
    * the current time.
    * 
    * @param values the values of the token, the first numInToken elements get filled in from the token
    * @param numInToken the number of values to pull out from the token
    * @param token the base64 encoded token
    * @return true if it decodes successfully, and as a side effect fills in values with any values which were encoded in token
    */
   public static boolean decode(Mac theMac, int[] values, int numInToken, String token) {
      ByteBuf tokenBuf = null;
      try {
         tokenBuf = Base64.decode(Unpooled.wrappedBuffer(token.getBytes()), Base64Dialect.URL_SAFE);

         ByteBufDataSource bds = new ByteBufDataSource(tokenBuf);
         for (int i = 0; i < numInToken; i++) {
            values[i] = bds.readVarInt();
         }
         String encoded = encode(theMac, values, numInToken);
         return encoded.equals(token);
      } catch (Exception e) {} finally {
         if (tokenBuf != null) {
            tokenBuf.release();
         }
      }
      return false;
   }
   
   /**
    * Return the number of minutes since NOT_THAT_LONG_AGO
    */
   public static int timeNowInMinutes() {
      long delta = System.currentTimeMillis() - NOT_THAT_LONG_AGO;
      return (int) (delta / 60000);
   }

   // encode/decode wrappers for known auth token types

   public static String encodeUserToken(int accountId, int entityId, int properties, int timeoutInMinutes) {
      int timeout = AuthToken.timeNowInMinutes() + timeoutInMinutes;
      int[] vals = { timeout, properties, accountId, entityId };
      return AuthToken.encode(MAC_SECURE, vals, 2);
   }

   public static String encodeAuthToken1Admin(int accountId, int val1, int timeoutInMinutes) {
      int timeout = AuthToken.timeNowInMinutes() + timeoutInMinutes;
      return AuthToken.encode(MAC_ADMIN, timeout, val1, accountId);
   }

   public static String encodeAuthToken2Secure(int accountId, int val1, int val2, int timeoutInMinutes) {
      int timeout = AuthToken.timeNowInMinutes() + timeoutInMinutes;
      return AuthToken.encode(MAC_SECURE, timeout, val1, val2, accountId);
   }
   
   public static String encodeAuthToken3Secure(int accountId, int val1, int val2, int val3, int timeoutInMinutes) {
      int timeout = AuthToken.timeNowInMinutes() + timeoutInMinutes;
      return AuthToken.encode(MAC_SECURE, timeout, val1, val2, val3, accountId);
   }

   public static String encodeAuthToken2Gates(int accountId, int val1, int val2, int timeoutInMinutes) {
      int timeout = AuthToken.timeNowInMinutes() + timeoutInMinutes;
      return AuthToken.encode(MAC_GATES, timeout, val1, val2, accountId);
   }

   public static String encodeAuthToken3Gates(int accountId, int val1, int val2, int val3, int timeoutInMinutes) {
      int timeout = AuthToken.timeNowInMinutes() + timeoutInMinutes;
      return AuthToken.encode(MAC_GATES, timeout, val1, val2, val3, accountId);
   }

   public static Decoded decodeUserToken(String token, int accountId, int entityId) {
      int[] vals = { 0, 0, accountId, entityId };
      if (!decode(MAC_SECURE, vals, 2, token)) {
         return null;
      }
      Decoded d = new Decoded();
      d.accountId = accountId;
      d.miscValues = new int[] { vals[1] };
      d.timeLeft = vals[0] - timeNowInMinutes();
      return d;
   }

   public static Decoded decodeAuthToken1Admin(String token) {
      int[] vals = new int[3];
      if (!decode(MAC_ADMIN, vals, 3, token)) {
         return null;
      }
      Decoded d = new Decoded();
      d.accountId = vals[2];
      d.miscValues = new int[] { vals[1] };
      d.timeLeft = vals[0] - timeNowInMinutes();
      return d;
   }

   public static Decoded decodeAuthToken2Secure(String token) {
      int[] vals = new int[4];
      if (!decode(MAC_SECURE, vals, 4, token)) {
         return null;
      }
      Decoded d = new Decoded();
      d.accountId = vals[3];
      d.miscValues = new int[] { vals[1], vals[2] };
      d.timeLeft = vals[0] - timeNowInMinutes();
      return d;
   }

   public static Decoded decodeAuthToken3Secure(String token) {
      int[] vals = new int[5];
      if (!decode(MAC_SECURE, vals, 5, token)) {
         return null;
      }
      Decoded d = new Decoded();
      d.accountId = vals[4];
      d.miscValues = new int[] { vals[1], vals[2], vals[3] };
      d.timeLeft = vals[0] - timeNowInMinutes();
      return d;
   }

   public static Decoded decodeAuthToken2Gates(String token) {
      int[] vals = new int[4];
      if (!decode(MAC_GATES, vals, 4, token)) {
         return null;
      }
      Decoded d = new Decoded();
      d.accountId = vals[3];
      d.miscValues = new int[] { vals[1], vals[2] };
      d.timeLeft = vals[0] - timeNowInMinutes();
      return d;
   }

   public static Decoded decodeAuthToken3Gates(String token) {
      int[] vals = new int[5];
      if (!decode(MAC_GATES, vals, 5, token)) {
         return null;
      }
      Decoded d = new Decoded();
      d.accountId = vals[4];
      d.miscValues = new int[] { vals[1], vals[2], vals[3] };
      d.timeLeft = vals[0] - timeNowInMinutes();
      return d;
   }

   public static class Decoded {
      public int   accountId;
      public int   timeLeft;
      public int[] miscValues;
   }

   public static String generateSharedSecret() {
      byte[] b = new byte[64];
      Random r = new SecureRandom();
      r.nextBytes(b);
      return Base64.encode(Unpooled.wrappedBuffer(b), Base64Dialect.STANDARD).toString(Charset.forName("UTF-8"));
   }

}
