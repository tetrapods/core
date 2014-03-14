package io.tetrapod.core.rpc;

public class Success extends Response {

   public final int code;

   public Success(int code) {
      this.code = code;
   }

   public boolean isError() {
      return true;
   }
}
