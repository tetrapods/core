package io.tetrapod.core.web;

import io.tetrapod.core.*;
import io.tetrapod.core.utils.Util;

import org.junit.*;


public class WebServerTest {
   
   @Test
   public void serveFiles() throws InterruptedException {
      TetrapodService service = new TetrapodService();
      final Server s = new Server(6777, new WebSessionFactory(service, "./webContent", false));
      new Thread(new Runnable() {
         @Override
         public void run() {
            Util.sleep(5000);
            s.stop();
         }
      }).start();
      s.start().sync().channel().closeFuture().sync();
      // try http://localhost:6777/api?requestId=1&toId=0&contractId=1&structId=10895179&1=221
      // does a register request with tag #1 (build) = 221
   }

}
