package io.tetrapod.core.rpc;

abstract public class Response extends Structure {
   
   public static final Response SUCCESS = new Success();

   public boolean isError() {
      return false;
   }

}
