package io.tetrapod.core.web;

import io.netty.channel.ChannelFuture;
import io.tetrapod.core.*;
import io.tetrapod.core.utils.Util;
import io.tetrapod.identity.IdentityService;

import org.junit.Test;


public class WebServerTest {
   
   @Test
   public void serveFiles() throws Exception {
      final int totalTestTime = 50000;
      
      TetrapodService pod = new TetrapodService();
      pod.startNetwork(null, "e:1");

      IdentityService ident = new IdentityService();
      ident.startNetwork("localhost", null);

      // start web server separately for now, should probably be an option in TetrapodService
      final Server s = new Server(6777, new WebSessionFactory(pod, pod, "./webContent", false));
      new Thread(new Runnable() {
         @Override
         public void run() {
            Util.sleep(totalTestTime);
            s.stop();
         }
      }).start();
      ChannelFuture close = s.start().sync().channel().closeFuture();
      
      close.sync();
      // try http://localhost:6777/api?requestId=1&toId=0&contractId=1&structId=10895179&1=221
      // does a register request with tag #1 (build) = 221
   }
   

}
