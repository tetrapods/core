package io.tetrapod.core.rpc;

import io.tetrapod.core.serialize.DataSource;

import java.io.IOException;

import org.junit.Test;
import org.slf4j.*;

public class RPCTest {
   public static final Logger logger = LoggerFactory.getLogger(RPCTest.class);

   public class MyResponse extends Response {
      @Override
      public void write(DataSource data) throws IOException {}

      @Override
      public void read(DataSource data) throws IOException {}

      @Override
      public int getStructId() {
         return 10001;
      }
      
      public int getContractId() {
         return 1;
      }
   }

   public class MyTestRequest extends Request {
      @Override
      public void write(DataSource data) throws IOException {}

      @Override
      public void read(DataSource data) throws IOException {}

      @Override
      public int getStructId() {
         return 10000;
      }

      public int getContractId() {
         return 1;
      }
}

   public interface MyResponseHandler {
      public void onResponse(MyResponse res, int errorCode);
   }

   @Test
   public void testAsync() {
      MyTestRequest req = new MyTestRequest();
      Async async = new Async(req, null);
      async.handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res, int errorCode) {
            logger.info("HANDLER: {} {}", res, errorCode);
         }
      });
      async.setResponse(new MyResponse(), 0);
      async.setResponse(null, 123);
   }
}
