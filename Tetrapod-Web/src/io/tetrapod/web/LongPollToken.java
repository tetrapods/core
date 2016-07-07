package io.tetrapod.web;

import javax.crypto.Mac;

import io.netty.handler.codec.base64.Base64Dialect;
import io.tetrapod.core.utils.AESEncryptor;
import io.tetrapod.core.utils.AuthToken;

/**
 * Signed token for a long poll session
 */
public class LongPollToken {

   private static Mac MAC_SESSION = null;

   public static class Decoded {
      public int clientId;
      public int timeLeft;
   }

   public synchronized static boolean setSecret(String secret) {
      return setSecret(AESEncryptor.decodeBase64(secret, Base64Dialect.STANDARD));
   }

   /**
    * Sets the shared secret. Needs to be called before this class is used. Returns false
    * if there is an error which would typically be Java not having strong crypto
    * available.
    */
   public synchronized static boolean setSecret(byte[] secret) {
      try {
         MAC_SESSION = AuthToken.createMac(secret, "Session".getBytes("UTF-8"));
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   public static String encodeToken(int clientId, int timeoutInMinutes) {
      return AuthToken.encode(MAC_SESSION, AuthToken.timeNowInMinutes() + timeoutInMinutes, clientId);
   }

   public static Decoded decodeToken(String token) {
      final int[] vals = new int[2];
      if (AuthToken.decode(MAC_SESSION, vals, 2, token)) {
         int timeLeft = vals[0] - AuthToken.timeNowInMinutes();
         if (timeLeft > 0) {
            Decoded d = new Decoded();
            d.timeLeft = timeLeft;
            d.clientId = vals[1];
            return d;
         }
      }
      return null;
   }

   public static Integer validateToken(String authToken) {
      final Decoded d = decodeToken(authToken);
      if (d != null) {
         return d.clientId;
      }
      return null;
   }

}
