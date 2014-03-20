package io.tetrapod.core;

import io.tetrapod.core.protocol.RegisterRequest;
import io.tetrapod.core.registry.Actor;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.Util;

import org.junit.Test;
import org.slf4j.*;

public class SessionTest {

   public static final Logger logger = LoggerFactory.getLogger(SessionTest.class);

   @Test
   public void testClientServer() throws Exception {
//      Dispatcher dispatcher = new Dispatcher();
//
//      Server server = new Server(12346, dispatcher);
//      server.start();
//
//      Client client = new Client("localhost", 12346, dispatcher);
//      Util.sleep(1000);
//
//      RegisterRequest req = new RegisterRequest();
//      req.build = 666;
//      client.getSession().sendRequest(req, 0, 0, Actor.TYPE_CLIENT, (byte) 30).handle(new ResponseHandler() {
//         @Override
//         public void onResponse(Response res, int errorCode) {
//            logger.info("Got my response. YEY! {} {}", res, errorCode);
//         }
//      });
//
//      Util.sleep(5000);
//
//      client.close();
//
//      Util.sleep(5000);
//
//      server.stop();
   }
}
