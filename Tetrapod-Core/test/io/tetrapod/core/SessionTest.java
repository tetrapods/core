package io.tetrapod.core;

import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.*;
import io.tetrapod.identity.IdentityService;
import io.tetrapod.protocol.core.RegisterRequest;

import org.junit.Test;
import org.slf4j.*;

public class SessionTest {

   public static final Logger logger = LoggerFactory.getLogger(SessionTest.class);

   @Test
   public void testClientServer() throws Exception {
      TetrapodService service = new TetrapodService();
      service.serviceInit(new Properties());

      IdentityService svc = new IdentityService();
      svc.networkInit(new Properties());
      svc.serviceInit(new Properties());

      Util.sleep(2000);

      svc.sendRequest(new RegisterRequest(666), 0).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res, int errorCode) {
            logger.info("Got my response. YEY! {} {}", res, errorCode);
            if (res != null) {
               logger.info("{}", res.dump());
            }
         }
      });

      //      Client client = new Client("localhost", 12346, dispatcher);
      //      Util.sleep(1000);
      //
      //      RegisterRequest req = new RegisterRequest();
      //      req.build = 666;
      //      client.getSession().sendRequest(req, 0, 0, EntityInfo.TYPE_CLIENT, (byte) 30).handle(new ResponseHandler() {
      //         @Override
      //         public void onResponse(Response res, int errorCode) {
      //            logger.info("Got my response. YEY! {} {}", res, errorCode);
      //         }
      //      });
      //
      //      Util.sleep(2000);
      //
      //      client.close();
      //
      Util.sleep(200000);

      service.stop();
   }
}
