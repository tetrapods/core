package io.tetrapod.core.rpc;

public class Request<T extends Response> {

   public Async<Request<T>, T> makeAsync() {
      return new Async<Request<T>, T>(this);
   }

   @Override
   public String toString() {
      return getClass().getSimpleName();
   }

}
