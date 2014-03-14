package io.tetrapod.core.rpc;

abstract public class Response extends Structure {

   public boolean isError() {
      return false;
   }

}
