package io.tetrapod.core;

import org.junit.Test;

public class SessionTest {

   @Test
   public void testClientServer() throws Exception {
      Dispatcher dispatcher = new Dispatcher();
      Server server = new Server(12345, dispatcher);
      server.start();

      Client client = new Client("localhost", 12345, dispatcher);

      Util.sleep(5000);

      client.close();

      Util.sleep(5000);

      server.stop();
   }

}
