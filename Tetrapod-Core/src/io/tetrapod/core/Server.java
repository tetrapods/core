package io.tetrapod.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.*;

import org.slf4j.*;

/**
 * A server that speaks the tetrapod wire protocol
 */
public class Server extends ChannelInitializer<SocketChannel> implements Session.Listener {

   public static final Logger    logger   = LoggerFactory.getLogger(Server.class);

   private Map<Integer, Session> sessions = new ConcurrentHashMap<>();

   private EventLoopGroup        bossGroup;

   private int                   port;
   private final Dispatcher      dispatcher;
   private final SessionFactory  sessionFactory;
   private SSLContext            sslContext;
   private boolean               clientAuth;

   public Server(int port, SessionFactory sessionFactory, Dispatcher dispatcher) {
      this.sessionFactory = sessionFactory;
      this.dispatcher = dispatcher;
      this.port = port;
   }

   public Server(int port, SessionFactory sessionFactory, Dispatcher dispatcher, SSLContext ctx, boolean clientAuth) {
      this(port, sessionFactory, dispatcher);
      enableTLS(ctx, clientAuth);
   }

   public void enableTLS(SSLContext ctx, boolean clientAuth) {
      this.sslContext = ctx;
      this.clientAuth = clientAuth;
   }

   public synchronized ChannelFuture start(int port) {
      this.port = port;
      return start();
   }

   public synchronized ChannelFuture start() {
      bossGroup = dispatcher.getBossGroup();
      ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, dispatcher.getWorkerGroup()).channel(NioServerSocketChannel.class);
      b.childHandler(this);
      setOptions(b);
      logger.info("Starting Server Listening on Port {}", port);
      return b.bind(port);
   }

   public void initChannel(SocketChannel ch) throws Exception {
      startSession(ch);
   }

   protected void setOptions(ServerBootstrap sb) {
      sb.option(ChannelOption.SO_BACKLOG, 128);
      sb.childOption(ChannelOption.SO_KEEPALIVE, true);
   }

   public void stop() {
      logger.debug("Stopping server on port {}...", port);
      try {
         bossGroup.shutdownGracefully().sync();
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
      }
      logger.debug("Stopped server on port {}", port);
   }

   private void startSession(final SocketChannel ch) throws Exception {
      logger.info("Connection from {}", ch);
      if (sslContext != null) {
         SSLEngine engine = sslContext.createSSLEngine();
         engine.setUseClientMode(false);
         engine.setWantClientAuth(clientAuth);

         // Netty 4.0.24 should mean we don't need this poodle hack anymore -- VERIFY:
         logger.warn("FIXME : Enabled Protocols = {}", engine.getEnabledProtocols().toString());
         // explicitly removes "SSLv3" from supported protocols to prevent the 'POODLE' exploit
         //engine.setEnabledProtocols(new String[] { "SSLv2Hello", "TLSv1", "TLSv1.1", "TLSv1.2" });

         SslHandler handler = new SslHandler(engine);
         ch.pipeline().addLast("ssl", handler);
      }
      Session session = sessionFactory.makeSession(ch);
      session.addSessionListener(this);
   }

   @Override
   public void onSessionStart(Session ses) {
      sessions.put(ses.getSessionNum(), ses);
   }

   @Override
   public void onSessionStop(Session ses) {
      sessions.remove(ses.getSessionNum());
   }

   public synchronized int getPort() {
      return port;
   }

   public int getNumSessions() {
      return sessions.size();
   }
}
