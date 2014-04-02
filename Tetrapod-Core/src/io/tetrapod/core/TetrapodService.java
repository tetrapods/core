package io.tetrapod.core;

import static io.tetrapod.protocol.core.TetrapodContract.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.Session.RelayHandler;
import io.tetrapod.core.registry.*;
import io.tetrapod.core.registry.Registry;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.StructureAdapter;
import io.tetrapod.core.utils.Util;
import io.tetrapod.core.web.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.service.*;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import org.slf4j.*;

/**
 * The tetrapod service is the core cluster service which handles message routing, cluster management, service discovery, and load balancing
 * of client connections
 */
public class TetrapodService extends DefaultService implements TetrapodContract.API, RelayHandler, Registry.RegistryBroadcaster {
   public static final Logger logger                  = LoggerFactory.getLogger(TetrapodService.class);

   public static final int    DEFAULT_PUBLIC_PORT     = 9900;
   public static final int    DEFAULT_SERVICE_PORT    = 9901;
   public static final int    DEFAULT_CLUSTER_PORT    = 9902;
   public static final int    DEFAULT_WEBSOCKETS_PORT = 9903;
   public static final int    DEFAULT_HTTP_PORT       = 9904;

   private final SecureRandom random                  = new SecureRandom();

   public final Registry      registry;

   private Topic              registryTopic;
   private Topic              servicesTopic;

   private Server             clusterServer;
   private Server             serviceServer;
   private Server             publicServer;
   private Server             webSocketsServer;
   private Server             httpServer;

   private final WebRoutes    webRoutes               = new WebRoutes();

   public TetrapodService() {
      registry = new Registry(this);
      setMainContract(new TetrapodContract());
   }

   @Override
   public void startNetwork(String hostAndPort, String token) throws Exception {

      publicServer = new Server(DEFAULT_PUBLIC_PORT, new TypedSessionFactory(Core.TYPE_ANONYMOUS));
      serviceServer = new Server(DEFAULT_SERVICE_PORT, new TypedSessionFactory(Core.TYPE_SERVICE));
      clusterServer = new Server(DEFAULT_CLUSTER_PORT, new TypedSessionFactory(Core.TYPE_TETRAPOD));
      webSocketsServer = new Server(DEFAULT_WEBSOCKETS_PORT, new WebSessionFactory("/sockets", true));
      httpServer = new Server(DEFAULT_HTTP_PORT, new WebSessionFactory("./webContent", false));

      if (hostAndPort == null) {
         // We're the first, so bootstrapping here
         // were not connecting anywhere, have to self register
         selfRegister(1);
         onReadyToServe();
      } else {
         this.token = token;
         // TODO: Join existing cluster:
         // 1: Connect and obtain a unique tetrapodId from cluster consensus
         // 2: selfRegister(tetrapodId)
         // 3: sync with all tetrapod registries
         // 4: onReadyToServe() to become active
      }
   }

   private void selfRegister(int tetrapodId) throws Exception {
      this.entityId = registry.setParentId(tetrapodId);
      final EntityInfo e = new EntityInfo(entityId, 0, random.nextLong(), Util.getHostName(), 0, Core.TYPE_TETRAPOD, getShortName(), 0, 0,
            getContractId());
      registry.register(e);
      logger.info(String.format("SELF-REGISTERING: 0x%08X %s", entityId, e));
      clusterServer.start().sync();
      // connect to self on localhost
      super.startNetwork("localhost:" + DEFAULT_CLUSTER_PORT, Token.encode(entityId, e.reclaimToken));
   }

   @Override
   public void onConnectedToCluster() {
      super.onConnectedToCluster();
      logger.info("Connected to Self");
      addSubscriptionHandler(new TetrapodContract.Registry(), registry);
   }

   public byte getEntityType() {
      return Core.TYPE_TETRAPOD;
   }

   private class TypedSessionFactory implements SessionFactory {
      private final byte type;

      private TypedSessionFactory(byte type) {
         this.type = type;
      }

      /**
       * Session factory for our sessions from clients and services
       */
      @Override
      public Session makeSession(SocketChannel ch) {
         final Session ses = new WireSession(ch, TetrapodService.this);
         ses.setMyEntityId(getEntityId());
         ses.setMyEntityType(Core.TYPE_TETRAPOD);
         ses.setTheirEntityType(type);
         ses.setRelayHandler(TetrapodService.this);
         ses.addSessionListener(new Session.Listener() {
            @Override
            public void onSessionStop(Session ses) {
               logger.info("Session Stopped: {}", ses);
               onChildEntityDisconnected(ses);
            }

            @Override
            public void onSessionStart(Session ses) {}
         });
         return ses;
      }
   }

   private class WebSessionFactory implements SessionFactory {
      public WebSessionFactory(String contentRoot, boolean webSockets) {
         this.contentRoot = contentRoot;
         this.webSockets = webSockets;
      }

      boolean webSockets = false;
      String  contentRoot;

      @Override
      public Session makeSession(SocketChannel ch) {
         TetrapodService pod = TetrapodService.this;
         Session ses = webSockets ? new WebSocketSession(ch, pod, contentRoot) : new WebHttpSession(ch, pod, contentRoot);
         ses.setRelayHandler(pod);
         ses.setMyEntityId(getEntityId());
         ses.setMyEntityType(Core.TYPE_TETRAPOD);
         ses.setTheirEntityType(Core.TYPE_CLIENT);
         // FIXME: for web admins we need to set this to Core.TYPE_ADMIN
         // But not sure how we distinguish this yet
         ses.addSessionListener(new Session.Listener() {
            @Override
            public void onSessionStop(Session ses) {
               logger.info("Session Stopped: {}", ses);
               onChildEntityDisconnected(ses);
            }

            @Override
            public void onSessionStart(Session ses) {}
         });
         return ses;
      }
   }

   protected void onChildEntityDisconnected(Session ses) {
      if (ses.getTheirEntityId() != 0) {
         registry.updateStatus(ses.getTheirEntityId(), status | Core.STATUS_GONE);
         final EntityInfo e = registry.getEntity(ses.getTheirEntityId());
         if (e != null) {
            e.setSession(null);
            // TODO: set all children to gone as well
         }
      }
   }

   @Override
   public void onRegistered() {
      registryTopic = registry.publish(entityId);
      servicesTopic = registry.publish(entityId);
   }

   /**
    * As a Tetrapod service, we can't start serving as one until we've registered & fully sync'ed with the cluster, or self-registered if we
    * are the first one. We call this once this criteria has been reached
    */
   private void onReadyToServe() {
      try {
         serviceServer.start().sync();
         publicServer.start().sync();
         webSocketsServer.start().sync();
         httpServer.start().sync();
      } catch (Exception e) {
         fail(e);
      }

      scheduleHealthCheck();
      updateStatus(status & ~Core.STATUS_INIT);
   }

   public void stop() {
      logger.info("STOP");
      publicServer.stop();
      serviceServer.stop();
      clusterServer.stop();
      webSocketsServer.stop();
      httpServer.stop();
   }

   @Override
   public void onShutdown(boolean restarting) {
      stop();
   }

   // ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   private Session findSession(final EntityInfo entity) {
      if (entity.parentId == getEntityId()) {
         return entity.getSession();
      } else {
         final EntityInfo parent = registry.getEntity(entity.parentId);
         assert (parent != null);
         return parent.getSession();
      }
   }

   @Override
   public Session getRelaySession(int entityId, int contractId) {
      EntityInfo entity = null;
      if (entityId == Core.UNADDRESSED) {
         entity = registry.getRandomAvailableService(contractId);
      } else {
         entity = registry.getEntity(entityId);
         if (entity == null) {
            logger.warn("Could not find an entity for {}", entityId);
         }
      }
      if (entity != null) {
         return findSession(entity);
      }
      return null;
   }

   @Override
   public void broadcast(MessageHeader header, ByteBuf buf) {
      // OPTIMIZE: Place on queue for each publisher
      final EntityInfo publisher = registry.getEntity(header.fromId);
      if (publisher != null) {
         final Topic topic = publisher.getTopic(header.topicId);
         if (topic != null) {
            synchronized (topic) { // FIXME: Won't need this sync if all topics processed on same thread
               for (final Subscriber s : topic.getSubscribers()) {
                  final EntityInfo e = registry.getEntity(s.entityId);
                  if (e != null) {
                     if (e.parentId == getEntityId() || e.isTetrapod()) {
                        final Session session = findSession(e);
                        if (session != null) {
                           int ri = buf.readerIndex();
                           boolean keepBroadcasting = publisher.parentId == getEntityId() && e.isTetrapod();
                           session.sendRelayedMessage(header, buf, keepBroadcasting);
                           buf.readerIndex(ri);
                        }
                     }
                  } else {
                     logger.error("Could not find subscriber {} for topic {}", s, topic);
                  }
               }
            }
         } else {
            logger.error("Could not find topic {} for entity {}", header.topicId, publisher);
         }
      } else {
         logger.error("Could not find publisher entity {}", header.fromId);
      }
   }

   @Override
   public WebRoutes getWebRoutes() {
      return webRoutes;
   }

   // ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public void broadcastRegistryMessage(Message msg) {
      broadcast(msg, registryTopic);
   }

   @Override
   public void broadcastServicesMessage(Message msg) {
      broadcast(msg, servicesTopic);
   }

   public void broadcast(Message msg, Topic topic) {
      logger.trace("BROADCASTING {} {}", topic, msg.dump());
      if (topic != null) {
         synchronized (topic) {
            sendBroadcastMessage(msg, topic.topicId);
         }
      }
   }

   // ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   private static class Token {
      int  entityId = 0;
      long nonce    = 0;

      public static Token decode(String token) {
         if (token == null)
            return null;
         // token is e:ENTITYID:r:RECLAIMNONCE. both e and r are optional
         Token t = new Token();
         String[] parts = token.split(":");
         for (int i = 0; i < parts.length; i += 2) {
            if (parts[i].equals("e")) {
               t.entityId = Integer.parseInt(parts[i + 1]);
            }
            if (parts[i].equals("r")) {
               t.nonce = Long.parseLong(parts[i + 1]);
            }
         }
         return t;
      }

      public static String encode(int entityId, long reclaimNonce) {
         return "e:" + entityId + ":r:" + reclaimNonce;
      }
   }

   // ////////////////////////////////////////////////////////////////////////////////////////

   private void scheduleHealthCheck() {
      dispatcher.dispatch(1, TimeUnit.SECONDS, new Runnable() {
         public void run() {
            if (dispatcher.isRunning()) {
               try {
                  healthCheck();
               } catch (Throwable e) {
                  logger.error(e.getMessage(), e);
               }
               scheduleHealthCheck();
            }
         }
      });
   }

   private void healthCheck() {
      // registry.logStats();
      for (EntityInfo e : registry.getChildren()) {
         if (e.isGone() && System.currentTimeMillis() - e.getGoneSince() > 60 * 1000) {
            logger.info("Reaping: {}", e);
            registry.unregister(e.entityId);
         }
      }
   }

   // ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public Response requestRegister(RegisterRequest r, RequestContext ctx) {
      if (getEntityId() == 0) {
         return new Error(ERROR_SERVICE_UNAVAILABLE);
      }
      EntityInfo info = null;
      final Token t = Token.decode(r.token);
      if (t != null) {
         info = registry.getEntity(t.entityId);
         if (info != null) {
            if (info.reclaimToken != t.nonce) {
               info = null; // return error instead?
            }
         }
      }
      if (info == null) {
         info = new EntityInfo();
         info.version = ctx.header.version;
         info.build = r.build;
         info.host = ctx.session.getPeerHostname();
         info.name = r.name;
         info.reclaimToken = random.nextLong();
         info.contractId = r.contractId;
      }

      info.status = r.status &= ~Core.STATUS_GONE;
      info.parentId = getEntityId();
      info.type = ctx.session.getTheirEntityType();
      if (info.type == Core.TYPE_ANONYMOUS) {
         info.type = Core.TYPE_CLIENT;
      }

      // register/reclaim
      registry.register(info);

      // update & store session
      ctx.session.setTheirEntityId(info.entityId);
      ctx.session.setTheirEntityType(info.type);

      info.setSession(ctx.session);

      return new RegisterResponse(info.entityId, info.parentId, Token.encode(info.entityId, info.reclaimToken));
   }

   @Override
   public Response requestUnregister(UnregisterRequest r, RequestContext ctx) {
      if (r.entityId != ctx.header.fromId && ctx.header.fromType != Core.TYPE_ADMIN) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      final EntityInfo info = registry.getEntity(r.entityId);
      if (info == null) {
         return new Error(ERROR_INVALID_ENTITY);
      }
      registry.unregister(info.entityId);
      return Response.SUCCESS;
   }

   @Override
   public Response requestPublish(PublishRequest r, RequestContext ctx) {
      if (ctx.header.fromType == Core.TYPE_TETRAPOD || ctx.header.fromType == Core.TYPE_SERVICE) {
         final EntityInfo entity = registry.getEntity(ctx.header.fromId);
         if (entity != null) {
            if (entity.parentId == getEntityId()) {
               final Topic t = registry.publish(ctx.header.fromId);
               if (t != null) {
                  return new PublishResponse(t.topicId);
               }
            } else {
               return new Error(ERROR_NOT_PARENT);
            }
         } else {
            return new Error(ERROR_INVALID_ENTITY);
         }
      }
      return new Error(ERROR_INVALID_RIGHTS);
   }

   @Override
   public Response requestRegistrySubscribe(RegistrySubscribeRequest r, RequestContext ctx) {
      if (registryTopic == null) {
         return new Error(ERROR_UNKNOWN);
      }
      synchronized (registryTopic) {
         broadcastRegistryMessage(new TopicSubscribedMessage(registryTopic.ownerId, registryTopic.topicId, ctx.header.fromId));
         // send all current entities
         for (EntityInfo e : registry.getChildren()) {
            sendMessage(new EntityRegisteredMessage(e, null), ctx.header.fromId, registryTopic.topicId);
         }
      }
      return Response.SUCCESS;
   }

   @Override
   public Response requestRegistryUnsubscribe(RegistryUnsubscribeRequest r, RequestContext ctx) {
      // TODO: validate 
      broadcastRegistryMessage(new TopicUnsubscribedMessage(registryTopic.ownerId, registryTopic.topicId, ctx.header.fromId));
      return Response.SUCCESS;
   }

   @Override
   public Response requestServicesSubscribe(ServicesSubscribeRequest r, RequestContext ctx) {
      if (servicesTopic == null) {
         return new Error(ERROR_UNKNOWN);
      }
      synchronized (servicesTopic) {
         broadcastRegistryMessage(new TopicSubscribedMessage(servicesTopic.ownerId, servicesTopic.topicId, ctx.header.fromId));
         // send all current entities
         for (EntityInfo e : registry.getServices()) {
            sendMessage(new ServiceAddedMessage(e), ctx.header.fromId, servicesTopic.topicId);
         }
      }
      return Response.SUCCESS;
   }

   @Override
   public Response requestServicesUnsubscribe(ServicesUnsubscribeRequest r, RequestContext ctx) {
      // TODO: validate 
      broadcastRegistryMessage(new TopicUnsubscribedMessage(servicesTopic.ownerId, servicesTopic.topicId, ctx.header.fromId));
      return Response.SUCCESS;
   }

   @Override
   public Response requestServiceStatusUpdate(ServiceStatusUpdateRequest r, RequestContext ctx) {
      // TODO: don't allow certain bits to be set from a request
      if (ctx.header.fromId != 0) {
         registry.updateStatus(ctx.header.fromId, r.status);
      }
      return Response.SUCCESS;
   }

   @Override
   public Response requestAddServiceInformation(AddServiceInformationRequest req, RequestContext ctx) {
      for (WebRoute r : req.routes)
         webRoutes.setRoute(r.path, r.contractId, r.structId);
      for (StructDescription sd : req.structs)
         StructureFactory.add(new StructureAdapter(sd));
      return Response.SUCCESS;
   }

   @Override
   public Response requestServiceIcon(ServiceIconRequest r, RequestContext ctx) {
      return new ServiceIconResponse("media/tetrapod.png");
   }

   @Override
   protected void registerServiceInformation() {
      // do nothing, our protocol is known by all tetrapods
   }

}
