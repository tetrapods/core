package io.tetrapod.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
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
public class Server implements Session.Listener {

   public static final Logger    logger      = LoggerFactory.getLogger(Server.class);

   private Map<Integer, Session> sessions    = new ConcurrentHashMap<>();

   private EventLoopGroup        bossGroup   = new NioEventLoopGroup();
   private EventLoopGroup        workerGroup = new NioEventLoopGroup();

   private int                   port;
   private final SessionFactory  sessionFactory;
   private SslHandler            ssl;

   public Server(int port, SessionFactory sessionFactory) {
      this.sessionFactory = sessionFactory;
      this.port = port;
   }

   public void enableTLS(SSLContext ctx, boolean clientAuth) {
      SSLEngine engine = ctx.createSSLEngine();
      engine.setUseClientMode(false);
      engine.setWantClientAuth(clientAuth);
      ssl = new SslHandler(engine);
   }

   public ChannelFuture start() {
      ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
         @Override
         public void initChannel(SocketChannel ch) throws Exception {
            startSession(ch);
         }
      }).option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true);

      logger.info("Starting Server Listening on Port {}", port);
      return b.bind(port);
   }

   public void stop() {
      logger.debug("Stopping server on port {}...", port);
      try {
         bossGroup.shutdownGracefully().sync();
         workerGroup.shutdownGracefully().sync();
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
      }
      logger.debug("Stopped server on port {}", port);
   }

   private void startSession(SocketChannel ch) {
      logger.info("Connection from {}", ch);
      if (ssl != null) {
         ch.pipeline().addLast(ssl);
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

   public int getPort() {
      return port;
   }

   public int getNumSessions() {
      return sessions.size();
   }
}
