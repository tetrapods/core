package io.tetrapod.core.web;

import io.tetrapod.core.*;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.ServerAddress;

import org.junit.Test;

public class WebServerTest {

   @Test
   public void serveFiles() throws Exception {
      final TetrapodService pod = new TetrapodService();
      pod.startNetwork(null, "e:1");

      TestService ident = new TestService();
      ident.startNetwork(new ServerAddress("localhost", TetrapodService.DEFAULT_SERVICE_PORT), null);

      Util.sleep(5000);
      pod.shutdown(false);
   }

}
