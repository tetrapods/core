package io.tetrapod.core.rpc;

abstract public class Response extends Structure {

   public static final Response SUCCESS = new Success();
   
   public static final Response error(int errorCode) {
      return new Error(errorCode);
   }

   public boolean isError() {
      return false;
   }

   public int errorCode() {
      if (isError()) {
         return ((Error) this).code;
      }
      return 0;
   }

}
