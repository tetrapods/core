package io.tetrapod.core.rpc;

import java.util.concurrent.CompletableFuture;

abstract public class Response extends Structure {

   public static final CompletableFuture<Success> SUCCESS_FUTURE = CompletableFuture.completedFuture(new Success());
   public static final Success SUCCESS = new Success();
   public static final Pending PENDING = new Pending();
   public static final CompletableFuture<Pending> PENDING_FUTURE = CompletableFuture.completedFuture(new Pending());
   public static final Pending ASYNC   = PENDING;      // an alternate name
   public static final CompletableFuture<Pending> ASYNC_FUTURE = PENDING_FUTURE;

   public static final Response error(int errorCode) {
      return new Error(errorCode);
   }

   public static final CompletableFuture<Response> errorFuture(int errorCode) {
      return CompletableFuture.completedFuture(new Error(errorCode));
   }


   public boolean isError() {
      return false;
   }

   final public boolean isSuccess() {
      return !isError(); // don't return true, subclasses don't override this one
   }

   public int errorCode() {
      if (isError()) {
         return ((Error) this).code;
      }
      return 0;
   }

}
