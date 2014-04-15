package io.tetrapod.core.web;

import java.util.*;

import io.tetrapod.core.*;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;

import org.junit.Test;

public class WebServerTest {

   @Test
   public void serveFiles() throws Exception {
      final TetrapodService pod = new TetrapodService();
      System.setProperty("sql.enabled", "false");
      Map<String, String> opts = new HashMap<>();
      pod.startNetwork(null, null, opts);
      while ((pod.getStatus() & Core.STATUS_STARTING) != 0) {
         Util.sleep(100);
      }

      TestService ident = new TestService();
      ident.startNetwork(new ServerAddress("localhost", TetrapodService.DEFAULT_SERVICE_PORT), null, opts);

      Util.sleep(2000);
      ident.shutdown(false);
      pod.shutdown(false);
      while (!pod.isTerminated()) {
         Util.sleep(100);
      }
   }

}
