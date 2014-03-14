package io.tetrapod.core.rpc;

public class Error extends Response {

   public final int code;

   public Error(int code) {
      this.code = code;
   }

   public boolean isError() {
      return true;
   }
}
