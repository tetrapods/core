package io.tetrapod.core.rpc;

import io.tetrapod.core.rpc.RPCTest.MyResponse;

public class MyTestRequest extends Request<MyResponse> {
   @Override
   public Async makeAsync() {
      return new Async(this);
   }

   public class Async extends io.tetrapod.core.rpc.Async<Request<MyResponse>, MyResponse> {
      public Async(Request<MyResponse> request) {
         super(request);
      }
   }

   public static interface Handler extends io.tetrapod.core.rpc.Async.Handler<RPCTest.MyResponse> {};

}
