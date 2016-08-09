package io.tetrapod.core.utils;

import java.util.Arrays;

import javax.crypto.Mac;

/**
 * Manages AuthTokens used for login and session accounts.  Note that login tokens are backwards compatible but
 * session tokens are not.
 * 
 * TODO : Add support for automatic key rotation, unit tests
 */
public class LoginAuthToken {

   private static String LOGIN_EXTRA   = "HkRzUvnYA5laYVkCHyFNViqLiP2GlzdMLAJnQZKqCMBQhZEhCZ"; // change if we have a breach
   private static String SESSION_EXTRA = "HkRzUvnYA5laYVkCHyFNViqLiP2GlzdMLAJnQZKqCMBQhZEhCZ"; // change if we have a breach

   private static Mac MAC_LOGIN   = null;
   private static Mac MAC_SESSION = null;

   public static class DecodedSession {
      public int accountId;
      public int timeLeft;
      public int userProperties;
      public int orgId;
   }

   public static class DecodedLogin {
      public int accountId;
      public int timeLeft;
      public int roomId;
      public int tokenFlags;
      public int orgId;
   }

   /**
    * Sets the shared secret. Needs to be called before this class is used. Returns false if there is an error which would typically be Java
    * not having strong crypto available.
    */
   public synchronized static boolean setSecret(byte[] secret) {
      try {
         MAC_LOGIN = AuthToken.createMac(Arrays.copyOfRange(secret, 1, secret.length - 1), LOGIN_EXTRA.getBytes());
         MAC_SESSION = AuthToken.createMac(Arrays.copyOfRange(secret, 2, secret.length - 2), SESSION_EXTRA.getBytes());
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   public static String encodeLoginToken(int accountId, int tokenFlags, int roomId, int timeoutInMinutes, int orgId) {
      return AuthToken.encode(MAC_LOGIN, AuthToken.timeNowInMinutes() + timeoutInMinutes, tokenFlags, roomId, accountId, orgId);
   }

   public static DecodedLogin decodeLoginToken(String token) {
      int[] vals = AuthToken.decode(MAC_LOGIN, token, 5);
      if (vals == null || vals.length == 0) {
         return null;
      }
      DecodedLogin d = new DecodedLogin();
      d.orgId = vals[4];
      d.accountId = vals[3];
      d.timeLeft = vals[0] - AuthToken.timeNowInMinutes();
      d.tokenFlags = vals[1];
      d.roomId = vals[2];
      return d;
   }

   public static String encodeSessionToken(int accountId, int userProperties, int entityId, int timeoutInMinutes, int orgId) {
      int timeout = AuthToken.timeNowInMinutes() + timeoutInMinutes;
      int[] vals = { timeout, userProperties, accountId, entityId, orgId };
      return AuthToken.encode(MAC_SESSION, vals, vals.length);
   }

   public static DecodedSession decodeSessionToken(String token, int accountId) {
      int[] vals = AuthToken.decode(MAC_SESSION, token, 5);
      if (vals == null || vals.length == 0 || vals[2] != accountId) {
         return null;
      }
      DecodedSession d = new DecodedSession();
      d.accountId = accountId;
      d.timeLeft = vals[0] - AuthToken.timeNowInMinutes();
      d.userProperties = vals[1];
      d.orgId = vals[4];
      return d;
   }
   public static DecodedSession decodeSessionToken(String token, int accountId, int entityId) {
      int[] vals = AuthToken.decode(MAC_SESSION, token, 5);
      if (vals == null || vals.length == 0 || vals[2] != accountId || vals[3] != entityId) {
         return null;
      }
      DecodedSession d = new DecodedSession();
      d.accountId = accountId;
      d.timeLeft = vals[0] - AuthToken.timeNowInMinutes();
      d.userProperties = vals[1];
      d.orgId = vals[4];
      return d;
   }

}
