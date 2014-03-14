package io.tetrapod.core.rpc;

import io.tetrapod.core.rpc.Async.Handler;

import org.junit.Test;
import org.slf4j.*;

public class RPCTest {
   public static final Logger logger = LoggerFactory.getLogger(RPCTest.class);

   public class MyResponse extends Response {}

   public class MyRequest extends Request<MyResponse> {
      @Override
      public Async makeAsync() {
         return new Async(this);
      }

      public class Async extends io.tetrapod.core.rpc.Async<Request<MyResponse>, MyResponse> {
         public Async(Request<MyResponse> request) {
            super(request);
         }
      }
   }

   public class MySimpleRequest extends Request<Success> {
      @Override
      public Async<Request<Success>, Success> makeAsync() {
         return new Async<Request<Success>, Success>(this);
      }
   }

   @Test
   public void testAsync() {
      MyRequest req = new MyRequest();
      MyRequest.Async async = req.makeAsync();
      async.setHandler(new Handler<RPCTest.MyResponse>() {
         @Override
         public void onResponse(MyResponse res, int errorCode) {
            logger.info("HANDLER: {} {}", res, errorCode);
         }
      });
      async.setResponse(new MyResponse(), 0);
      async.setResponse(null, 123);
   }

}
