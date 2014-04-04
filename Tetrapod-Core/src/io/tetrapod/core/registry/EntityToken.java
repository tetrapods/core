package io.tetrapod.core;

public class EntityToken {
   int  entityId = 0;
   long nonce    = 0;

   public static EntityToken decode(String token) {
      if (token == null)
         return null;
      // token is e:ENTITYID:r:RECLAIMNONCE. both e and r are optional
      EntityToken t = new EntityToken();
      String[] parts = token.split(":");
      for (int i = 0; i < parts.length; i += 2) {
         if (parts[i].equals("e")) {
            t.entityId = Integer.parseInt(parts[i + 1]);
         }
         if (parts[i].equals("r")) {
            t.nonce = Long.parseLong(parts[i + 1]);
         }
      }
      return t;
   }

   public static String encode(int entityId, long reclaimNonce) {
      return "e:" + entityId + ":r:" + reclaimNonce;
   }
}
