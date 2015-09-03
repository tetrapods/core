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
      int[] vals = new int[4];
      if (!AuthToken.decode(MAC_LEGACY, vals, 4, token)) {
         return 0;
      }
      return vals[2];
   }
   
   public synchronized static int decodeTwitter(String token) {
      int[] vals = new int[5];
      if (!AuthToken.decode(MAC_LEGACY, vals, 5, token)) {
         return 0;
      }
      return vals[1];
   }


}
