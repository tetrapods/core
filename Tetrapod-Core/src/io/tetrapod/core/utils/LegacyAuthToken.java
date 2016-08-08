package io.tetrapod.core.utils;

import javax.crypto.Mac;

public class LegacyAuthToken {

   private static Mac MAC_LEGACY  = null;

   public synchronized static boolean setSecret(byte[] secret) {
      try {
         MAC_LEGACY = AuthToken.createMacLegacy(secret);
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   public synchronized static int decodePublic(String token) {
      int[] vals = AuthToken.decode(token, 4);
      if (vals == null || vals.length == 0) {
         return 0;
      }
      return vals[2];
   }
   
   public synchronized static int decodeTwitter(String token) {
      int[] vals = AuthToken.decode(token, 5);
      if (vals == null || vals.length == 0) {
         return 0;
      }
      return vals[1];
   }


}
