package io.tetrapod.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.tetrapod.core.protocol.TetrapodContract;

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
   private StructureFactory      factory     = new StructureFactory();

   public Server(int port, Dispatcher dispatcher) {
      this.port = port;
      this.dispatcher = dispatcher;
      this.incomingAPI(new TetrapodContract(), 0);
      this.outgoingAPI(new TetrapodContract(), 0);
   }
   
   public void incomingAPI(Contract c, int dynamicId) {
      c.addRequests(factory, dynamicId);
   }
   
   public void outgoingAPI(Contract c, int dynamicId) {
      c.addResponses(factory, dynamicId);
   }
   
   public void incomingMessages(Contract s, int dynamicId) {
      s.addMessages(factory, dynamicId);
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
      Session session = new Session(ch, dispatcher, factory);
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
