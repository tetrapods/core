package io.tetrapod.core.utils;

import javax.crypto.Mac;

/**
 * Manages AuthTokens used for login and session accounts.  Note that login tokens are backwards compatable but
 * session tokens are not.
 * 
 * TODO : Add support for automatic key rotation, unit tests
 */
public class LegacyAuthToken {


   private static Mac MAC_LEGACY  = null;

   public static class DecodedLegacy {
      public int anonAccountId;
      public int roomId;
   }

   /**
    * Sets the shared secret. Needs to be called before this class is used. Returns false if there is an error which would typically be Java
    * not having strong crypto available.
    */
   public synchronized static boolean setSecret(byte[] secret) {
      try {
         MAC_LEGACY = AuthToken.createMacLegacy(secret);
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   public synchronized static DecodedLegacy decodePublicToken(String token) {
      int[] vals = new int[4];
      if (!AuthToken.decode(MAC_LEGACY, vals, 4, token)) {
         return null;
      }
      DecodedLegacy d = new DecodedLegacy();
      d.anonAccountId = vals[3];
      d.roomId = vals[2];
      return d;
   }

}
