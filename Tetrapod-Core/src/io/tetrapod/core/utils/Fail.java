package io.tetrapod.core.utils;

public class Fail {

   public interface FailHandler {
      public void fail(Throwable e);

      public void fail(String msg);
   }

   public static FailHandler handler;

   public static RuntimeException fail(Throwable e) {
      if (handler != null) {
         handler.fail(e);
      }
      return new RuntimeException(e);
   }

   public static void fail(String msg) {
      if (handler != null) {
         handler.fail(msg);
      }
   }

}
