package io.tetrapod.core.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class AuthTest {
   
   @Test
   public void success() throws Exception {
      Auth.setSecret("thisisasecrettestsecrettest".getBytes());
      String encoded = Auth.encode(1092, 894, Auth.timeNowInMinutes() - 5, 1);
      int perms = Auth.decode(1092, 894, encoded);
      assertEquals(1, perms);
   }
   
   @Test
   public void timeout() throws Exception {
      Auth.setSecret("thisisasecrettestsecrettest".getBytes());
      String encoded = Auth.encode(1092, 894, Auth.timeNowInMinutes() - Auth.EXPIRY_MINS - 1, 1);
      int perms = Auth.decode(1092, 894, encoded);
      assertEquals(-2, perms);
   }

   @Test
   public void invalidAccount() throws Exception {
      Auth.setSecret("thisisasecrettestsecrettest".getBytes());
      String encoded = Auth.encode(1092, 894, 892318, 1);
      int perms = Auth.decode(1091, 894, encoded);
      assertEquals(-1, perms);
   }

   @Test
   public void invalidEntityId() throws Exception {
      Auth.setSecret("thisisasecrettestsecrettest".getBytes());
      String encoded = Auth.encode(1092, 894, 892318, 1);
      int perms = Auth.decode(1092, 895, encoded);
      assertEquals(-1, perms);
   }
   
   @Test
   public void badToken() throws Exception {
      Auth.setSecret("thisisasecrettestsecrettest".getBytes());
      String encoded = Auth.encode(1092, 894, 892318, 1);
      encoded = encoded + "1";
      int perms = Auth.decode(1092, 895, encoded);
      assertEquals(-1, perms);
   }

   @Test
   public void emptyToken() throws Exception {
      Auth.setSecret("thisisasecrettestsecrettest".getBytes());
      String encoded = Auth.encode(1092, 894, 892318, 1);
      encoded = "";
      int perms = Auth.decode(1092, 895, encoded);
      assertEquals(-3, perms);
   }
   
   @Test
   public void tinyToken() throws Exception {
      Auth.setSecret("thisisasecrettestsecrettest".getBytes());
      String encoded = Auth.encode(1092, 894, 892318, 1);
      encoded = "a";
      int perms = Auth.decode(1092, 895, encoded);
      assertEquals(-3, perms);
   }
   
   @Test
   public void notBase64Token() throws Exception {
      Auth.setSecret("thisisasecrettestsecrettest".getBytes());
      String encoded = Auth.encode(1092, 894, 892318, 1);
      encoded = "!!@$%^&*()_)__--__++=={[}]::'<,>.?/";
      int perms = Auth.decode(1092, 895, encoded);
      assertEquals(-3, perms);
   }





}
