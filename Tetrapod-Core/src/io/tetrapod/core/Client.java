package io.tetrapod.core;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import org.slf4j.*;

/**
 * A client that speaks the tetrapod wire protocol
 */
public class Client implements Session.Listener {
   public static final Logger logger = LoggerFactory.getLogger(Client.class);

   private Session            session;
   private Service            service;

   public Client(Service service) {
      this.service = service;
   }

   public ChannelFuture connect(String host, int port, Dispatcher dispatcher) throws Exception {
      Bootstrap b = new Bootstrap();
      b.group(dispatcher.getWorkerGroup());
      b.channel(NioSocketChannel.class);
      b.option(ChannelOption.SO_KEEPALIVE, true);
      b.handler(new ChannelInitializer<SocketChannel>() {
         @Override
         public void initChannel(SocketChannel ch) throws Exception {
            startSession(ch);
         }
      });
      return b.connect(host, port).sync();
   }

   private void startSession(SocketChannel ch) {
      logger.info("Connection to {}", ch);
      // TODO: ch.pipeline().addLast(sslEngine);
      session = new Session(ch, service);
      session.addSessionListener(this);
   }

   public void close() {
      session.close();
   }

   public Session getSession() {
      return session;
   }

   @Override
   public void onSessionStart(Session ses) {
      logger.debug("Connection Started", ses);
   }

   @Override
   public void onSessionStop(Session ses) {
      logger.debug("Connection Closed", ses);
   }

}
