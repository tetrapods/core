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

public class AESEncryptor {
   public static final Logger  logger = LoggerFactory.getLogger(AESEncryptor.class);

   public static final Charset UTF8   = Charset.forName("UTF-8");

   /**
    * Requires:
    * http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html
    * 
    * On Mac OS X copy jars to (replacing jdk version with appropriate one):
    * /Library/Java/JavaVirtualMachines/jdk1.8.0_60.jdk/Contents/Home/jre/lib/security/
    */

   public static SecretKey makeKey(char[] password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      KeySpec spec = new PBEKeySpec(password, salt, 65536, 256);
      return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
   }

   public static String encodeBase64(byte[] data) {
      return encodeBase64(data, Base64Dialect.URL_SAFE);
   }

   public static String encodeBase64(byte[] data, Base64Dialect dialect) {
      ByteBuf buf = Unpooled.wrappedBuffer(data);
      try {
         ByteBuf encodedBuf = Base64.encode(buf, dialect);
         try {
            return encodedBuf.toString(UTF8);
         } finally {
            encodedBuf.release();
         }
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

   public static String encryptSaltedAES(String str, SecretKey key) {
      if (str == null) {
         return null;
      }
      return encodeBase64(encryptSaltedAES(str.getBytes(UTF8), key));
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

   public static String decryptSaltedAES(String str, SecretKey key) {
      if (str == null) {
         return null;
      }
      return new String(decryptSaltedAES(decodeBase64(str), key), UTF8);
   }

   public static byte[] decryptSaltedAES(byte[] data, SecretKey key) {
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

   public static String encryptAES(String string, String key) {
      try {
         Key k = new SecretKeySpec(key.getBytes(), "AES");
         Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
         c.init(Cipher.ENCRYPT_MODE, k);
         byte[] encoded = c.doFinal(string.getBytes());
         return encodeBase64(encoded);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public static String decryptAES(String encryptedData, String key) {
      try {
         Key k = new SecretKeySpec(key.getBytes(), "AES");
         Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
         c.init(Cipher.DECRYPT_MODE, k);
         byte[] decoded = c.doFinal(decodeBase64(encryptedData));
         return new String(decoded);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public static void main(String[] args) {
      if (args[0].equals("enc")) {
         System.out.println("Encrypted: " + encryptAES(args[1], args[2]));
      } else if (args[0].equals("dec")) {
         System.out.println("Decrypted: " + decryptAES(args[1], args[2]));
      }
   }
}
