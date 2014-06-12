package io.tetrapod.core.rpc;

abstract public class Response extends Structure {

   public static final Success SUCCESS = new Success();
   public static final Pending PENDING = new Pending();

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

   public boolean isGenericSuccess() {
      Response s = Response.SUCCESS;
      return s.getContractId() == getContractId() && s.getStructId() == getStructId();
   }

}
