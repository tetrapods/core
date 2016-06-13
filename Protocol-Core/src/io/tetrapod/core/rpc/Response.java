package io.tetrapod.core.rpc;

abstract public class Response extends Structure {

   public static final Success SUCCESS = new Success();
   public static final Pending PENDING = new Pending();
   public static final Pending ASYNC   = PENDING;      // an alternate name

   public static final Response error(int errorCode) {
      return new Error(errorCode);
   }

   public boolean isError() {
      return false;
   }

   final public boolean isSuccess() {
      return !isError(); // don't return true, subclasses don't override this one
   }

   public int errorCode() {
      if (isError()) {
         return ((Error) this).code;
      }
      return 0;
   }

}
