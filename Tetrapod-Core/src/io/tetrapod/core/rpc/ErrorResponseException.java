package io.tetrapod.core.rpc;

public class ErrorResponseException extends RuntimeException {  
   private static final long serialVersionUID = 1L;
   
   public final int errorCode;

   public ErrorResponseException(int errorCode) {
      this.errorCode = errorCode;
   }
}
