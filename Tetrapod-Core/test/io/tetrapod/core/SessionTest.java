package io.tetrapod.core;

import io.tetrapod.core.protocol.RegisterRequest;

import org.junit.Test;

public class SessionTest {

   @Test
   public void testClientServer() throws Exception {
      Dispatcher dispatcher = new Dispatcher();
      Server server = new Server(12345, dispatcher);
      server.start();

      Client client = new Client("localhost", 12345, dispatcher);
      Util.sleep(1000);

      RegisterRequest req = new RegisterRequest();
      req.build = 666;
      client.getSession().sendRequest(req, 0, 0, (byte)30);

      Util.sleep(500000);

      client.close();

      Util.sleep(500000);

      server.stop();
   }

}
