package io.tetrapod.core.rpc;

public class Response {

   public boolean isError() {
      return false;
   }

   @Override
   public String toString() {
      return getClass().getSimpleName();
   }

}
