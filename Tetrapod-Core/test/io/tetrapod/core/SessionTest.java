package io.tetrapod.core;

import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.*;
import io.tetrapod.identity.IdentityService;
import io.tetrapod.protocol.service.PauseRequest;

import org.junit.Test;
import org.slf4j.*;

public class SessionTest {

   public static final Logger logger = LoggerFactory.getLogger(SessionTest.class);

   @Test
   public void testClientServer() throws Exception {

      TetrapodService service = new TetrapodService();
      service.serviceInit(new Properties());

      Util.sleep(1000);

      IdentityService svc1 = new IdentityService();
      svc1.networkInit(new Properties());
      svc1.serviceInit(new Properties());

      Util.sleep(1000);

      IdentityService svc2 = new IdentityService();
      svc2.networkInit(new Properties());
      svc2.serviceInit(new Properties());

      Util.sleep(1000);

      svc1.sendRequest(new PauseRequest(), svc2.getEntityId()).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res, int errorCode) {
            logger.info("Got my response. YEY! {} {}", res, errorCode);
         }
      });

      Util.sleep(2000);

      service.stop();
   }
}
