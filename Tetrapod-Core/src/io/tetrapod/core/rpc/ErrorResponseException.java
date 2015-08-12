package io.tetrapod.core.rpc;

public class ErrorResponseException extends RuntimeException {  
   private static final long serialVersionUID = 1L;
   
   public final int errorCode;

   public ErrorResponseException(int errorCode) {
      super("ErrorCode = " + errorCode);
      this.errorCode = errorCode;
   }
   public ErrorResponseException(int errorCode, String extra) {
      super("ErrorCode = " + errorCode + " " + extra);
      this.errorCode = errorCode;
   }
}
