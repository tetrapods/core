package io.tetrapod.core;

import static org.junit.Assert.assertTrue;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;

import java.util.*;

import org.junit.*;
import org.slf4j.*;

@Ignore
public class SessionTest {

   public static final Logger logger = LoggerFactory.getLogger(SessionTest.class);

   @Test
   public void testClientServer() throws Exception {

      Map<String, String> opts = new HashMap<>();
      TetrapodService pod = new TetrapodService();
      System.setProperty("sql.enabled", "false");
      pod.startNetwork(null, null, opts);
      while ((pod.getStatus() & Core.STATUS_STARTING) != 0) {
         Util.sleep(100);
      }

      Util.sleep(1000);
      TestService svc1 = new TestService();
      svc1.startNetwork(new ServerAddress("localhost", Core.DEFAULT_SERVICE_PORT), null, opts);
      assertTrue(svc1.getEntityId() > 0);

      TestService svc2 = new TestService();
      svc2.startNetwork(new ServerAddress("localhost", Core.DEFAULT_SERVICE_PORT), null, opts);
      Util.sleep(1000);
      assertTrue(svc2.getEntityId() > 0);

      svc1.sendRequest(new PauseRequest(), svc2.getEntityId())
               .handle(res -> logger.info("Got my response. YEY! {} {}", res, res.errorCode()));

      svc1.addSubscriptionHandler(new TetrapodContract.Services(), new TetrapodContract.Services.API() {

         @Override
         public void messageServiceAdded(ServiceAddedMessage m, MessageContext ctx) {
            logger.info("GOT MESSAGE!!!! {}", m.dump());
         }

         @Override
         public void genericMessage(Message message, MessageContext ctx) {}

         @Override
         public void messageServiceRemoved(ServiceRemovedMessage m, MessageContext ctx) {}

         @Override
         public void messageServiceUpdated(ServiceUpdatedMessage m, MessageContext ctx) {}

      });

      Util.sleep(2000);

      svc2.sendMessage(new ServiceAddedMessage(), svc1.getEntityId());

      Util.sleep(2000);

      svc1.shutdown(false);
      svc2.shutdown(false);
      pod.shutdown(false);
      while (!pod.isTerminated()) {
         Util.sleep(100);
      }
   }
}
