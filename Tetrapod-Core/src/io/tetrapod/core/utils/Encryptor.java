package io.tetrapod.core.utils;

import io.netty.buffer.*;
import io.netty.handler.codec.base64.*;

import java.nio.charset.Charset;
import java.security.*;
import java.security.spec.*;
import java.util.Arrays;

import javax.crypto.*;
import javax.crypto.spec.*;
import javax.xml.bind.DatatypeConverter;

import org.slf4j.*;

public class Encryptor {
   public static final Logger   logger = LoggerFactory.getLogger(Encryptor.class);

   private static final Charset UTF8   = Charset.forName("UTF-8");

   /**
    * Requires: http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html
    * 
    * On Mac OS X copy jars to (replacing jdk version with appropriate one):
    * /Library/Java/JavaVirtualMachines/jdk1.7.0_51.jdk/Contents/Home/jre/lib/security/
    */

   public static String encryptStable(String s) {
      return instance.encryptStableAES(s);
   }

   public static String decryptStable(String s) {
      return instance.decryptStableAES(s);
   }

   public static String encryptSalted(String s) {
      return instance.encryptSaltedAES(s);
   }

   public static String decryptSalted(String s) {
      return instance.decryptSaltedAES(s);
   }

   public static byte[] encryptSalted(byte[] b) {
      return instance.encryptSaltedAES(b);
   }

   public static byte[] decryptSalted(byte[] b) {
      return instance.decryptSaltedAES(b);
   }

   public static byte[] getRandStringKey() {
      return instance.randStringKey.getEncoded();
   }

   public static Encryptor make(String key, String salt, String keySalt) {
      String o_key = Util.getProperty("encryption.key", "locallocallocal");
      String o_salt = Util.getProperty("encryption.salt", "thisissomesaltthisissomesaltthisissomesalt");
      String o_keySalt = Util.getProperty("encryption.keysalt", o_salt);

      System.setProperty("encryption.key", key);
      System.setProperty("encryption.salt", salt);
      System.setProperty("encryption.keysalt", keySalt);

      Encryptor e = new Encryptor();

      System.setProperty("encryption.key", o_key);
      System.setProperty("encryption.salt", o_salt);
      System.setProperty("encryption.keysalt", o_keySalt);

      return e;
   }

   private static final Encryptor instance = new Encryptor();
   private final String           checksum;
   private final SecretKey        randStringKey;
   private final SecretKey        stableStringKey;
   private final SecretKey        randByteKey;
   private final byte[]           stableSalt;

   protected Encryptor() {
      try {

         String[] keys = keyGen(3);
         String staticSalt = Util.getProperty("encryption.salt", "thisissomesaltthisissomesaltthisissomesalt");

         String keySalt = Util.getProperty("encryption.keysalt", staticSalt);
         byte[] salt = Arrays.copyOf(keySalt.getBytes(UTF8), 8);

         checksum = digest(staticSalt + keySalt + keys[0] + keys[1] + keys[2]);
         logger.info("Encryptor Checksum = {}", checksum);

         randStringKey = makeKey(keys[0].toCharArray(), salt);
         stableStringKey = makeKey(keys[1].toCharArray(), salt);
         randByteKey = makeKey(keys[2].toCharArray(), salt);
         stableSalt = Arrays.copyOf(staticSalt.getBytes(UTF8), 16);
      } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
         throw new RuntimeException(e);
      }
   }

   private String[] keyGen(int num) {
      String k = Util.getProperty("encryption.key", "locallocallocal");
      String[] keys = new String[num];
      for (int i = 0; i < num; i++) {
         keys[i] = k.substring(i + 2) + k.substring(0, i + 1);
      }
      return keys;
   }

   private static SecretKey makeKey(char[] password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      KeySpec spec = new PBEKeySpec(password, salt, 65536, 256);
      return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
   }

   public String encryptStableAES(String str) {
      if (str == null) {
         return null;
      }
      try {
         Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
         cipher.init(Cipher.ENCRYPT_MODE, stableStringKey, new IvParameterSpec(stableSalt));
         return encodeBase64(cipher.doFinal(str.getBytes(UTF8)));
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public String decryptStableAES(String str) {
      if (str == null) {
         return null;
      }
      try {
         Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
         cipher.init(Cipher.DECRYPT_MODE, stableStringKey, new IvParameterSpec(stableSalt));
         return new String(cipher.doFinal(decodeBase64(str)), UTF8);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public String encryptSaltedAES(String str) {
      if (str == null) {
         return null;
      }
      return encodeBase64(encryptSaltedAES(str.getBytes(UTF8), randStringKey));
   }

   public String decryptSaltedAES(String str) {
      if (str == null) {
         return null;
      }
      return new String(decryptSaltedAES(decodeBase64(str), randStringKey), UTF8);
   }

   public byte[] encryptSaltedAES(byte[] data) {
      return encryptSaltedAES(data, randByteKey);
   }

   public byte[] decryptSaltedAES(byte[] data) {
      return decryptSaltedAES(data, randByteKey);
   }

   public static String encodeBase64(byte[] data) {
      ByteBuf buf = Unpooled.wrappedBuffer(data);
      try {
         return Base64.encode(buf, Base64Dialect.URL_SAFE).toString(UTF8);
      } finally {
         buf.release();
      }
   }

   public static byte[] decodeBase64(String str) {
      return decodeBase64(str, Base64Dialect.URL_SAFE);
   }

   public static byte[] decodeBase64(String str, Base64Dialect dialect) {
      ByteBuf tokenBuf = null;
      try {
         tokenBuf = Base64.decode(Unpooled.wrappedBuffer(str.getBytes(UTF8)), dialect);
         byte[] decoded = new byte[tokenBuf.readableBytes()];
         tokenBuf.readBytes(decoded);
         return decoded;
      } finally {
         if (tokenBuf != null) {
            tokenBuf.release();
         }
      }
   }

   public static byte[] encryptSaltedAES(byte[] data, SecretKey key) {
      try {
         Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
         cipher.init(Cipher.ENCRYPT_MODE, key);
         AlgorithmParameters params = cipher.getParameters();
         byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();
         byte[] ciphertext = cipher.doFinal(data);
         assert (iv.length == 16);
         byte[] buf = new byte[16 + ciphertext.length];
         System.arraycopy(iv, 0, buf, 0, 16);
         System.arraycopy(ciphertext, 0, buf, 16, ciphertext.length);
         return buf;
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public byte[] decryptSaltedAES(byte[] data, SecretKey key) {
      try {
         byte[] iv = Arrays.copyOf(data, 16);
         Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
         cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
         return cipher.doFinal(data, 16, data.length - 16);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Returns a md5 hash string for this file
    */
   public static String digest(String string) {
      try {
         MessageDigest md = MessageDigest.getInstance("MD5");
         return DatatypeConverter.printHexBinary(md.digest(string.getBytes(UTF8))).toUpperCase();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public static void verifyChecksum() {
      final String checksum = Util.getProperty("encryption.checksum");
      if (checksum != null) {
         if (!instance.checksum.equals(checksum)) {
            throw new RuntimeException("Encryptor Checksum does not match: " + instance.checksum + " != " + checksum);
         }
      }

   }

}
