package io.tetrapod.core;

import static org.junit.Assert.assertTrue;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.service.PauseRequest;

import org.junit.Test;
import org.slf4j.*;

public class SessionTest {

   public static final Logger logger = LoggerFactory.getLogger(SessionTest.class);

   @Test
   public void testClientServer() throws Exception {

      TetrapodService service = new TetrapodService();
      service.startNetwork(null, null);

      Util.sleep(1000);
      TestService svc1 = new TestService();
      svc1.startNetwork(new ServerAddress("localhost", TetrapodService.DEFAULT_SERVICE_PORT), null);
      Util.sleep(1000);
      assertTrue(svc1.getEntityId() > 0);

      TestService svc2 = new TestService();
      svc2.startNetwork(new ServerAddress("localhost", TetrapodService.DEFAULT_SERVICE_PORT), null);
      Util.sleep(1000);
      assertTrue(svc2.getEntityId() > 0);

      svc1.sendRequest(new PauseRequest(), svc2.getEntityId()).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            logger.info("Got my response. YEY! {} {}", res, res.errorCode());
         }
      });

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
         public void messageServiceStats(ServiceStatsMessage m, MessageContext ctx) {}

         @Override
         public void messageServiceUpdated(ServiceUpdatedMessage m, MessageContext ctx) {}

      });

      Util.sleep(2000);

      svc2.sendMessage(new ServiceAddedMessage(), svc1.getEntityId(), Core.UNADDRESSED);

      service.broadcastRegistryMessage(new EntityUpdatedMessage(svc2.getEntityId(), 0));

      Util.sleep(2000);

      service.shutdown(false);
      while (!service.isTerminated()) {
         Util.sleep(100);
      }
   }
}
