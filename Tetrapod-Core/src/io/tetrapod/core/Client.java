package io.tetrapod.core;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.tetrapod.core.protocol.TetrapodContract;

import org.slf4j.*;

/**
 * A client that speaks the tetrapod wire protocol
 */
public class Client implements Session.Listener {
   public static final Logger logger = LoggerFactory.getLogger(Client.class);

   private Session            session;
   private StructureFactory   factory     = new StructureFactory();

   public Client(final String host, final int port, final Dispatcher dispatcher) throws Exception {
      outgoingAPI(new TetrapodContract(), 0);
      Bootstrap b = new Bootstrap();
      b.group(dispatcher.getWorkerGroup());
      b.channel(NioSocketChannel.class);
      b.option(ChannelOption.SO_KEEPALIVE, true);
      b.handler(new ChannelInitializer<SocketChannel>() {
         @Override
         public void initChannel(SocketChannel ch) throws Exception {
            startSession(ch, dispatcher);
         }
      });
      b.connect(host, port).sync();
   }

   private void startSession(SocketChannel ch, Dispatcher dispatcher) {
      logger.info("Connection to {}", ch);
      // TODO: ch.pipeline().addLast(sslEngine);
      session = new Session(ch, dispatcher, factory);
      session.addSessionListener(this);
   }
   
   public void outgoingAPI(Contract c, int dynamicId) {
      c.addResponses(factory, dynamicId);
   }
   
   public void incomingMessages(Contract s, int dynamicId) {
      s.addMessages(factory, dynamicId);
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
