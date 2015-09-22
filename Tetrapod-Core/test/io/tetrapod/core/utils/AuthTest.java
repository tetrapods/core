package io.tetrapod.core.utils;

public class AuthTest {
   /*
   @Test
   public void successNone() throws Exception {
      AuthToken.setSecret("thisisasecrettestsecrettest".getBytes());
      int[] vals = { 1000, 2000, -3000 }; 
      String encoded = AuthToken.encode(vals, 0);
      assertTrue(AuthToken.decode(vals, 0, encoded));
   }

   @Test
   public void successAll() throws Exception {
      AuthToken.setSecret("thisisasecrettestsecrettest".getBytes());
      int[] vals = { 1000, 2000, -3000 }; 
      String encoded = AuthToken.encode(vals, 3);
      vals = new int[] { 0, 0, 0 }; 
      assertTrue(AuthToken.decode(vals, 3, encoded));
   }

   @Test
   public void largeSuccess() throws Exception {
      AuthToken.setSecret("thisisasecrettestsecrettest".getBytes());
      int[] vals = new int[1000];
      for (int i = 0; i < vals.length; i++) {
         vals[i] = 30000 + i;
      }
      String encoded = AuthToken.encode(vals, 100);
      for (int i = 0; i < 100; i++) {
         vals[i] = 0;
      }
      assertTrue(AuthToken.decode(vals, 100, encoded));
   }

   @Test
   public void fails() throws Exception {
      AuthToken.setSecret("thisisasecrettestsecrettest".getBytes());
      int[] vals = { 1000, 2000, -3000 }; 
      String encoded = AuthToken.encode(vals, 0);
      vals[0]++;
      assertFalse(AuthToken.decode(vals, 0, encoded));
   }

   @Test
   public void badToken() throws Exception {
      AuthToken.setSecret("thisisasecrettestsecrettest".getBytes());
      int[] vals = { 1000, 2000, -3000 }; 
      String encoded = AuthToken.encode(vals, 0);
      encoded += "1";
      assertFalse(AuthToken.decode(vals, 0, encoded));
   }

   @Test
   public void emptyToken() throws Exception {
      int[] vals = { 1000, 2000, -3000 }; 
      String encoded = AuthToken.encode(vals, 0);
      encoded = "";
      assertFalse(AuthToken.decode(vals, 0, encoded));
   }
   
   @Test
   public void tinyToken() throws Exception {
      int[] vals = { 1000, 2000, -3000 }; 
      String encoded = AuthToken.encode(vals, 0);
      encoded = "a";
      assertFalse(AuthToken.decode(vals, 0, encoded));
   }
   
   @Test
   public void notBase64Token() throws Exception {
      int[] vals = { 1000, 2000, -3000 }; 
      String encoded = AuthToken.encode(vals, 0);
      encoded = "Grr! Arg! LOL";
      assertFalse(AuthToken.decode(vals, 0, encoded));
   }
    */
}
