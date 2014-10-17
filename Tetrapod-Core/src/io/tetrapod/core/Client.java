package io.tetrapod.core;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.*;

import org.slf4j.*;

/**
 * A client that speaks the tetrapod wire protocol
 */
public class Client implements Session.Listener {
   public static final Logger   logger = LoggerFactory.getLogger(Client.class);

   private final SessionFactory factory;
   private Session              session;
   private SslHandler           ssl;

   public Client(SessionFactory factory) {
      this.factory = factory;
   }

   public void enableTLS(SSLContext ctx) {
      SSLEngine engine = ctx.createSSLEngine();
      engine.setUseClientMode(true);
      engine.setNeedClientAuth(false);
      
      // explicitly removes "SSLv3" from supported protocols to prevent the 'POODLE' exploit
      engine.setEnabledProtocols(new String[] { "SSLv2Hello", "TLSv1", "TLSv1.1", "TLSv1.2" });
      
      ssl = new SslHandler(engine);
   }

   public ChannelFuture connect(final String host, final int port, final Dispatcher dispatcher) throws Exception {
      return connect(host, port, dispatcher, null);
   }

   public ChannelFuture connect(final String host, final int port, final Dispatcher dispatcher, final Session.Listener listener)
         throws Exception {
      Bootstrap b = new Bootstrap();
      b.group(dispatcher.getWorkerGroup());
      b.channel(NioSocketChannel.class);
      b.option(ChannelOption.SO_KEEPALIVE, true);
      b.handler(new ChannelInitializer<SocketChannel>() {
         @Override
         public void initChannel(SocketChannel ch) throws Exception {
            logger.info("Connection to {}:{}", host, port);
            startSession(ch);
            if (listener != null) {
               session.addSessionListener(listener);
            }
         }
      });
      return b.connect(host, port);
   }

   private synchronized void startSession(SocketChannel ch) {
      if (ssl != null) {
         ch.pipeline().addLast(ssl);
      }
      session = factory.makeSession(ch);
      session.addSessionListener(this);
   }

   public synchronized void close() {
      if (session != null) {
         session.close();
      }
   }

   public synchronized Session getSession() {
      return session;
   }

   public synchronized boolean isConnected() {
      return session == null ? false : session.isConnected();
   }

   @Override
   public void onSessionStart(Session ses) {
      logger.trace("Connection Started", ses);
   }

   @Override
   public void onSessionStop(Session ses) {
      logger.trace("Connection Closed", ses);
   }

}
