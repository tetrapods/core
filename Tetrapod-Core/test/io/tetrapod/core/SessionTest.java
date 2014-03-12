package io.tetrapod.core;

import org.junit.Test;

public class SessionTest {

   @Test
   public void testClientServer() throws Exception {
      Dispatcher dispatcher = new Dispatcher();
      Server server = new Server(12345, dispatcher);
      server.start();

      @SuppressWarnings("unused")
      Client client = new Client("localhost", 12345, dispatcher);

      while (true) {
         Util.sleep(1000);
      }
   }

}
