package io.tetrapod.core.utils;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;

import org.slf4j.*;

/**
 * A singleton AES Encryptor with keys initialized from properties.
 */
public class Encryptor {
   public static final Logger logger = LoggerFactory.getLogger(Encryptor.class);

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

      Util.setProperty("encryption.key", key);
      Util.setProperty("encryption.salt", salt);
      Util.setProperty("encryption.keysalt", keySalt);

      Encryptor e = new Encryptor();

      Util.setProperty("encryption.key", o_key);
      Util.setProperty("encryption.salt", o_salt);
      Util.setProperty("encryption.keysalt", o_keySalt);

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
         byte[] salt = Arrays.copyOf(keySalt.getBytes(AESEncryptor.UTF8), 8);

         checksum = AESEncryptor.digest(staticSalt + keySalt + keys[0] + keys[1] + keys[2]);
         logger.info("Encryptor Checksum = {}", checksum);

         randStringKey = AESEncryptor.makeKey(keys[0].toCharArray(), salt);
         stableStringKey = AESEncryptor.makeKey(keys[1].toCharArray(), salt);
         randByteKey = AESEncryptor.makeKey(keys[2].toCharArray(), salt);
         stableSalt = Arrays.copyOf(staticSalt.getBytes(AESEncryptor.UTF8), 16);
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

   public String encryptStableAES(String str) {
      if (str == null) {
         return null;
      }
      try {
         Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
         cipher.init(Cipher.ENCRYPT_MODE, stableStringKey, new IvParameterSpec(stableSalt));
         return AESEncryptor.encodeBase64(cipher.doFinal(str.getBytes(AESEncryptor.UTF8)));
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
         return new String(cipher.doFinal(AESEncryptor.decodeBase64(str)), AESEncryptor.UTF8);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public String encryptSaltedAES(String str) {
      return AESEncryptor.encryptSaltedAES(str, randStringKey);
   }

   public String decryptSaltedAES(String str) {
      if (str == null) {
         return null;
      }
      return AESEncryptor.decryptSaltedAES(str, randStringKey);
   }

   public byte[] encryptSaltedAES(byte[] data) {
      return AESEncryptor.encryptSaltedAES(data, randByteKey);
   }

   public byte[] decryptSaltedAES(byte[] data) {
      return AESEncryptor.decryptSaltedAES(data, randByteKey);
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
