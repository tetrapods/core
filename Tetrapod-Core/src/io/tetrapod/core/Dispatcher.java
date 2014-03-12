package io.tetrapod.core;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

/**
 * Manages service-wide thread-pools and dispatch of requests and sequenced behaviors
 */
public class Dispatcher {
   private EventLoopGroup workerGroup = new NioEventLoopGroup();

   public EventLoopGroup getWorkerGroup() {
      return workerGroup;
   }

   //LinkedTransferQueue<E>

}
