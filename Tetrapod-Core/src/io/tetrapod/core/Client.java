package io.tetrapod.core;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public class Client {
   private Session session;

   public Client(final String host, final int port, final Dispatcher dispatcher) throws Exception {
      Bootstrap b = new Bootstrap();
      b.group(dispatcher.getWorkerGroup());
      b.channel(NioSocketChannel.class);
      b.option(ChannelOption.SO_KEEPALIVE, true);
      b.handler(new ChannelInitializer<SocketChannel>() {
         @Override
         public void initChannel(SocketChannel ch) throws Exception {
            // TODO: ch.pipeline().addLast(sslEngine);
            session = new Session(ch, dispatcher);
            //session.addSessionListener(this);
         }
      });
      b.connect(host, port).sync();
   }

   public void close() {
      session.close();
   }
}
