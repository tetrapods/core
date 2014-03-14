package io.tetrapod.core.rpc;

import org.junit.Test;
import org.slf4j.*;

public class RPCTest {
   public static final Logger logger = LoggerFactory.getLogger(RPCTest.class);

   public class MyResponse extends Response {}

   public class MySimpleRequest extends Request<Success> {
      @Override
      public Async<Request<Success>, Success> makeAsync() {
         return new Async<Request<Success>, Success>(this);
      }
   }

   @Test
   public void testAsync() {
      MyTestRequest req = new MyTestRequest();
      MyTestRequest.Async async = req.makeAsync();
      async.setHandler(new MyTestRequest.Handler() {
         @Override
         public void onResponse(MyResponse res, int errorCode) {
            logger.info("HANDLER: {} {}", res, errorCode);
         }
      });
      async.setHandler(req.Handler);
      async.setResponse(new MyResponse(), 0);
      async.setResponse(null, 123);
   }
}
