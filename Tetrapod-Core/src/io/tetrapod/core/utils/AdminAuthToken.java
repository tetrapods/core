package io.tetrapod.core.utils;

import javax.crypto.Mac;

import io.netty.handler.codec.base64.Base64Dialect;
import io.tetrapod.core.rpc.ErrorResponseException;
import io.tetrapod.protocol.core.CoreContract;

/**
 * Manages AuthTokens used for Tetrapod Admin accounts.
 * 
 * TODO : Add support for automatic key rotation, unit tests
 */
public class AdminAuthToken {

   public static final String SHARED_SECRET_KEY = "tetrapod.shared.secret";

   private static Mac         MAC_LOGIN         = null;
   private static Mac         MAC_SESSION       = null;

   public static class Decoded {
      public int  accountId;
      public int  timeLeft;
      public long rights;
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
         MAC_LOGIN = AuthToken.createMac(secret, "Login".getBytes("UTF-8"));
         MAC_SESSION = AuthToken.createMac(secret, "Session".getBytes("UTF-8"));
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   public static String encodeLoginToken(int accountId, int timeoutInMinutes) {
      return AuthToken.encode(MAC_LOGIN, AuthToken.timeNowInMinutes() + timeoutInMinutes, accountId);
   }

   public static int decodeLoginToken(String token) {
      final int[] vals = AuthToken.decode(MAC_LOGIN, token, 2);
      if (vals != null && vals.length > 0) {
         int timeLeft = vals[0] - AuthToken.timeNowInMinutes();
         if (timeLeft > 0) {
            return vals[1]; // accountId
         }
      }
      return 0;
   }

   public static String encodeSessionToken(int accountId, int timeoutInMinutes, long rights) {
      return AuthToken.encode(MAC_SESSION, AuthToken.timeNowInMinutes() + timeoutInMinutes, accountId, (int) (rights & 0xFFFFFFFF),
            (int) (rights >> 32));
   }

   public static Decoded decodeSessionToken(String token) {
      final int[] vals = AuthToken.decode(MAC_SESSION, token, 4);
      if (vals != null && vals.length > 0) {
         int timeLeft = vals[0] - AuthToken.timeNowInMinutes();
         if (timeLeft > 0) {
            Decoded d = new Decoded();
            d.accountId = vals[1];
            d.timeLeft = timeLeft;
            d.rights = vals[2] | (vals[3] << 32);
            return d;
         }
      }
      return null;
   }

   public static void validateAdminToken(String authToken, long requiredRights) {
      final Decoded d = decodeSessionToken(authToken);
      if (d == null) {
         throw new ErrorResponseException(CoreContract.ERROR_INVALID_RIGHTS);
      }
      if ((d.rights & requiredRights) != requiredRights) {
         throw new ErrorResponseException(CoreContract.ERROR_INVALID_RIGHTS);
      }
   }

   public static void validateAdminToken(int accountId, String adminToken, long requiredRights) {
      final Decoded d = decodeSessionToken(adminToken);
      if (d == null || d.accountId != accountId) {
         throw new ErrorResponseException(CoreContract.ERROR_INVALID_RIGHTS);
      }
      if ((d.rights & requiredRights) != requiredRights) {
         throw new ErrorResponseException(CoreContract.ERROR_INVALID_RIGHTS);
      }
   }

}
