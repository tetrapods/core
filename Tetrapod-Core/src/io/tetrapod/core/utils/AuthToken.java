package io.tetrapod.core.utils;

import io.netty.buffer.*;
import io.netty.handler.codec.base64.Base64;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Helper class used to authenticate messages.  Works by takes a message (and array of ints)
 * and computing an HMAC using a shared secret.  The token is then a subset(input vals) + HMAC.
 * Upon decoding the subset of input vals are recovered, combined with any values known through
 * other means, and the HMAC is recomputed and checked for validity. 
 * <p>
 * Note that the actual data encoded and decoded in the token is up to the caller.
 */
public class AuthToken {

   private static final long NOT_THAT_LONG_AGO = 1395443029600L;
   private static Mac        MAC               = null;

   /**
    * Sets the shared secret.  Needs to be called before this class is used. Returns false if
    * there is an error which would typically be Java not having strong crypto available.
    */
   public static boolean setSecret(byte[] secret) {
      try {
         // append NTLA so updating it will invalidate old tokens
         byte[] ntla = Long.toHexString(NOT_THAT_LONG_AGO).getBytes();
         byte[] key = Arrays.copyOf(secret, secret.length + ntla.length);
         System.arraycopy(ntla, 0, key, secret.length, ntla.length);
         SecretKeySpec signingKey = new SecretKeySpec(key, "HmacSHA1");
         Mac macCoder = Mac.getInstance("HmacSHA1");
         macCoder.init(signingKey);
         synchronized (AuthToken.class) {
            MAC = macCoder;
         }
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   /**
    * Encodes a auth token. The numInToken parameter is a bandwidth micro-optimization.  Say for
    * example we wanted to make an auth token with one value being the entityId.  We *could* encode
    * the true value for the entity id in the token, but since it is already present in the header
    * we could leave it out of the token and supply it at decode time.
    * 
    * @param values the values which form the basis of the 
    * @param numInToken the number of values which will need to be encoded inside the token
    * @return the base64 encoded token
    */
   public static String encode(int[] values, int numInToken) {
      try {
         ByteBuf buf = Unpooled.buffer();
         ByteBufDataSource bds = new ByteBufDataSource(buf);
         for (int i = 0; i < values.length; i++) {
            bds.writeVarInt(values[i]);
         }
         byte[] mac;
         synchronized (AuthToken.class) {
            MAC.update(buf.array(), buf.arrayOffset(), buf.writerIndex());
            mac = MAC.doFinal();
         }
         buf.resetWriterIndex();
         for (int i = 0; i < numInToken; i++) {
            bds.writeVarInt(values[i]);
         }
         String plainText = Base64.encode(buf).toString(Charset.forName("UTF-8"));
         String macText = Base64.encode(Unpooled.wrappedBuffer(mac)).toString(Charset.forName("UTF-8"));
         return plainText + macText;
      } catch (IOException e) {

      }
      return null;
   }
   
   /**
    * Decodes an auth token.  If there is at least one value in the token it assumes the first
    * value is a timeout value and checks it versus the current time.
    * 
    * @param values the values of the token, the first numInToken elemenets get filled in from the token
    * @param numInToken the number of values to pull out from the token
    * @param token the base64 encoded token
    * @param timedOut treu if there is at least one value and the first value is less than the current time
    * @return true if it decodes succesfully, and as a side effect fills in values with any values which were encoded in token
    */
   public static boolean decode(int[] values, int numInToken, String token, Value<Boolean> timedOut) {
      try {
         ByteBuf tokenBuf = Base64.decode(Unpooled.wrappedBuffer(token.getBytes()));
         ByteBufDataSource bds = new ByteBufDataSource(tokenBuf);
         for (int i = 0; i < numInToken; i++) {
            values[i] = bds.readVarInt(); 
         }
         if (values.length > 0) {
            timedOut.set(values[0] < timeNowInMinutes());
         }
         String encoded = encode(values, numInToken);
         return encoded.equals(token);
      } catch (Exception e) {
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

}
