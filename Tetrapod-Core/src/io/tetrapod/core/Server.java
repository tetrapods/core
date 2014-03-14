package io.tetrapod.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.*;

/**
 * A server that speaks the tetrapod wire protocol
 */
public class Server implements Session.Listener {

   public static final Logger    logger      = LoggerFactory.getLogger(Server.class);

   private Map<Integer, Session> sessions    = new ConcurrentHashMap<>();

   private EventLoopGroup        bossGroup   = new NioEventLoopGroup();
   private EventLoopGroup        workerGroup = new NioEventLoopGroup();

   private Dispatcher            dispatcher;
   private int                   port;

   public Server(int port, Dispatcher dispatcher) {
      this.port = port;
      this.dispatcher = dispatcher;
   }

   public void start() throws Exception {
      ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
         @Override
         public void initChannel(SocketChannel ch) throws Exception {
            startSession(ch);
         }
      }).option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true);
      b.bind(port).sync();
      logger.info("Listening on port {}", port);
   }

   public void stop() {
      try {
         bossGroup.shutdownGracefully().sync();
         workerGroup.shutdownGracefully().sync();
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
      }
   }

   private void startSession(SocketChannel ch) {
      logger.info("Connection from {}", ch);
      // TODO: add ssl to pipeline if configured 
      // ch.pipeline().addLast(sslEngine);
      Session session = new Session(ch, dispatcher);
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

}
