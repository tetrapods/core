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

      Util.sleep(2000);

      service.stop();
   }
}
