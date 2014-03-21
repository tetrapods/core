package io.tetrapod.core.utils;

import io.netty.buffer.*;
import io.netty.handler.codec.base64.Base64;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.*;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Auth {

   public static final int   EXPIRY_MINS       = 30;
   private static final long NOT_THAT_LONG_AGO = 1395443029600L;
   private static Mac        MAC               = null;

   public static void setSecret(byte[] secret) throws NoSuchAlgorithmException {
      // append NTLA so updating it will invalidate old tokens
      byte[] ntla = Long.toHexString(NOT_THAT_LONG_AGO).getBytes();
      byte[] key = Arrays.copyOf(secret, secret.length + ntla.length);
      System.arraycopy(ntla, 0, key, secret.length, ntla.length);
      SecretKeySpec signingKey = new SecretKeySpec(key, "HmacSHA1");
      Mac macCoder = Mac.getInstance("HmacSHA1");
      try {
         macCoder.init(signingKey);
         synchronized (Auth.class) {
            MAC = macCoder;
         }
      } catch (InvalidKeyException e) {
         throw new NoSuchAlgorithmException(e);
      }
   }

   /**
    * Encodes the given value is as a verifiable token. Encoding is a base64 string of:
    * <ul>
    * <li>creation time
    * <li>permissions
    * <li>hmac of (accountId entityId time permissions)
    * </ul>
    */
   public static String encode(int accountId, int entityId, int permissions) {
      return encode(accountId, entityId, timeNowInMinutes(), permissions);
   }

   protected static String encode(int accountId, int entityId, int time, int permissions) {
      try {
         ByteBuf buf = Unpooled.buffer();
         ByteBufDataSource bds = new ByteBufDataSource(buf);
         bds.writeVarInt(entityId);
         bds.writeVarInt(accountId);
         bds.writeVarInt(time);
         bds.writeVarInt(permissions);
         byte[] mac;
         synchronized (Auth.class) {
            MAC.update(buf.array(), buf.arrayOffset(), buf.writerIndex());
            mac = MAC.doFinal();
         }
         buf.resetWriterIndex();
         bds.writeVarInt(time);
         bds.writeVarInt(permissions);
         String plaintext = Base64.encode(buf).toString(Charset.forName("UTF-8"));
         String encoded = Base64.encode(Unpooled.wrappedBuffer(mac)).toString(Charset.forName("UTF-8"));
         return plaintext + encoded;
      } catch (IOException e) {

      }
      return null;
   }

   /**
    * Decodes the token and checks if it's valid and not timed out. Returns the permissions of the
    * given accountId if still valid. Or -1 if invalid or -2 if timed out or -3 on io error.
    */
   public static int decode(int accountId, int entityId, String token) {
      try {
         ByteBuf tokenBuf = Base64.decode(Unpooled.wrappedBuffer(token.getBytes()));
         ByteBufDataSource bds = new ByteBufDataSource(tokenBuf);
         int time = bds.readVarInt();
         int permissions = bds.readVarInt();
         String encoded = encode(accountId, entityId, time, permissions);
         if (!encoded.equals(token))
            return -1;
         if (timeNowInMinutes() - time > EXPIRY_MINS)
            return -2;
         return permissions;
      } catch (Exception e) {

      }
      return -3;
   }

   /**
    * Return the number of minutes since NOT_THAT_LONG_AGO
    */
   public static int timeNowInMinutes() {
      long delta = System.currentTimeMillis() - NOT_THAT_LONG_AGO;
      return (int) (delta / 60000);
   }

}
