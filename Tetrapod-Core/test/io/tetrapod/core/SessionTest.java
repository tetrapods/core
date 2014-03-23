package io.tetrapod.core;

import static org.junit.Assert.assertTrue;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.Util;
import io.tetrapod.identity.IdentityService;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.service.PauseRequest;

import org.junit.Test;
import org.slf4j.*;

public class SessionTest {

   public static final Logger logger = LoggerFactory.getLogger(SessionTest.class);

   @Test
   public void testClientServer() throws Exception {

      TetrapodService service = new TetrapodService();
      service.startNetwork(null, "e:1");

      Util.sleep(1000);
      IdentityService svc1 = new IdentityService();
      svc1.startNetwork("localhost", null);
      Util.sleep(1000);
      assertTrue(svc1.getEntityId() > 0);

      IdentityService svc2 = new IdentityService();
      svc2.startNetwork("localhost", null);
      Util.sleep(1000);
      assertTrue(svc2.getEntityId() > 0);

      svc1.sendRequest(new PauseRequest(), svc2.getEntityId()).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            logger.info("Got my response. YEY! {} {}", res, res.errorCode());
         }
      });

      Util.sleep(2000);

      svc2.sendMessage(new ServiceAddedMessage(999, "Test!"), svc1.getEntityId(), Core.UNADDRESSED);

      Util.sleep(2000);

      service.stop();
   }
}
