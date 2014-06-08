package io.tetrapod.core.rpc;

public class ErrorResponseException extends RuntimeException {
   public final int errorCode;

   public ErrorResponseException(int errorCode) {
      this.errorCode = errorCode;
   }
}
