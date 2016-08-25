package io.tetrapod.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslHandler;

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
   private ChannelFuture         channel;

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
      channel = b.bind(port);
      return channel;
   }

   @Override
   public void initChannel(SocketChannel ch) throws Exception {
      startSession(ch);
   }

   protected void setOptions(ServerBootstrap sb) {
      sb.option(ChannelOption.SO_BACKLOG, 128);
      sb.childOption(ChannelOption.SO_KEEPALIVE, true);
   }

   public void close() {
      if (channel != null) {
         Channel c = channel.channel();
         try {
            c.close();
         } catch (Exception e) {
            logger.error(e.getMessage(), e);
         }
         logger.debug("Stopped server on port {}", port);
      }
   }

   public void stop() {
      logger.debug("Stopping server on port {}...", port);
      try {
         if (bossGroup != null) {
            bossGroup.shutdownGracefully().sync();
         }
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
      }
      logger.debug("Stopped server on port {}", port);
   }

   public void purge() {
      for (Session session : sessions.values()) {
         session.close();
      }
   }

   private static final Set<String> WEAK_CIPHERS = new HashSet<>();
   static {
      WEAK_CIPHERS.add("SSL_RSA_WITH_RC4_128_MD5");
      WEAK_CIPHERS.add("SSL_RSA_WITH_RC4_128_SHA");
      WEAK_CIPHERS.add("SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA");
      WEAK_CIPHERS.add("TLS_ECDHE_RSA_WITH_RC4_128_SHA");
      WEAK_CIPHERS.add("TLS_DHE_RSA_WITH_AES_128_CBC_SHA");
      WEAK_CIPHERS.add("TLS_DHE_RSA_WITH_AES_128_CBC_SHA256");
      WEAK_CIPHERS.add("TLS_DHE_RSA_WITH_AES_256_CBC_SHA");
      WEAK_CIPHERS.add("TLS_DHE_RSA_WITH_AES_256_CBC_SHA256");
      WEAK_CIPHERS.add("TLS_DHE_RSA_WITH_AES_256_CBC_SHA256");
      
      //EDH-RSA-DES-CBC3-SHA
   }

   private void startSession(final SocketChannel ch) throws Exception {
      logger.debug("Connection from {}", ch);
      if (sslContext != null) {
         SSLEngine engine = sslContext.createSSLEngine();
         engine.setUseClientMode(false);
         engine.setWantClientAuth(clientAuth);

         // Explicitly removes "SSLv3" from supported protocols to prevent the 'POODLE' exploit
         final List<String> protocols = new ArrayList<>();
         for (String p : engine.getEnabledProtocols()) {
            if (!p.equals("SSLv3")) {
               protocols.add(p);
            }
         }
         engine.setEnabledProtocols(protocols.toArray(new String[protocols.size()]));

         // remove weak cipher suites from the enabled list
         final List<String> ciphers = new ArrayList<>();
         for (String p : engine.getEnabledCipherSuites()) {
            if (!WEAK_CIPHERS.contains(p)) {
               ciphers.add(p);
            } else {
               logger.trace("removing weak cipher suit {}", p);
            }
         }
         engine.setEnabledCipherSuites(ciphers.toArray(new String[ciphers.size()]));

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
