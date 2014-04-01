package io.tetrapod.core.web;

import io.tetrapod.core.*;
import io.tetrapod.core.utils.Util;

import org.junit.Test;


public class WebServerTest {
   
   @Test
   public void serveFiles() throws Exception {
      final TetrapodService pod = new TetrapodService();
      pod.startNetwork(null, "e:1");

      TestService ident = new TestService();
      ident.startNetwork("localhost", null);

      Util.sleep(5000);
      pod.stop();
   }

}
