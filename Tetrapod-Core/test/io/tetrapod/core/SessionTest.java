package io.tetrapod.core;

import static org.junit.Assert.assertTrue;
import io.tetrapod.core.utils.Util;
import io.tetrapod.identity.IdentityService;

import org.junit.Test;
import org.slf4j.*;

public class SessionTest {

   public static final Logger logger = LoggerFactory.getLogger(SessionTest.class);

   @Test
   public void testClientServer() throws Exception {
      TetrapodService service = new TetrapodService();
      service.startNetwork(null, null);

      IdentityService svc = new IdentityService();
      svc.startNetwork("localhost", null);

      Util.sleep(2000);

      assertTrue(svc.getEntityId() > 0);

      Util.sleep(2000);

      service.stop();
   }
}
