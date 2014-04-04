package io.tetrapod.core;

import static io.tetrapod.protocol.core.TetrapodContract.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.Session.RelayHandler;
import io.tetrapod.core.registry.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.StructureAdapter;
import io.tetrapod.core.utils.*;
import io.tetrapod.core.web.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.service.*;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.*;

/**
 * The tetrapod service is the core cluster service which handles message routing, cluster management, service discovery, and load balancing
 * of client connections
 */
public class TetrapodService extends DefaultService implements TetrapodContract.API, RelayHandler,
      io.tetrapod.core.registry.Registry.RegistryBroadcaster {

   public static final Logger                         logger                  = LoggerFactory.getLogger(TetrapodService.class);

   public static final int                            DEFAULT_PUBLIC_PORT     = 9900;
   public static final int                            DEFAULT_SERVICE_PORT    = 9901;
   public static final int                            DEFAULT_CLUSTER_PORT    = 9902;
   public static final int                            DEFAULT_WEBSOCKETS_PORT = 9903;
   public static final int                            DEFAULT_HTTP_PORT       = 9904;

   protected final SecureRandom                       random                  = new SecureRandom();

   protected final io.tetrapod.core.registry.Registry registry;

   private Topic                                      clusterTopic;
   private Topic                                      registryTopic;
   private Topic                                      servicesTopic;

   private final TetrapodCluster                      cluster;

   private Server                                     serviceServer;
   private Server                                     publicServer;
   private Server                                     webSocketsServer;
   private Server                                     httpServer;

   private final WebRoutes                            webRoutes               = new WebRoutes();

   private final Properties                           properties              = new Properties();

   private long                                       lastStatsLog;

   public TetrapodService() {
      // HACK Properties hack for now
      for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
         if (e.getKey().toString().startsWith("tetrapod.")) {
            properties.put(e.getKey().toString(), e.getValue().toString());
         }
      }
      cluster = new TetrapodCluster(this, properties);
      registry = new io.tetrapod.core.registry.Registry(this);
      setMainContract(new TetrapodContract());
   }

   @Override
   public void startNetwork(ServerAddress address, String token) throws Exception {
      logger.info(" ***** Start Network ***** ");
      cluster.startListening();
      if (address == null && token == null) {
         // we're not connecting anywhere, so bootstrap and self register as the first
         registerSelf(io.tetrapod.core.registry.Registry.BOOTSTRAP_ID, random.nextLong());
      } else {
         // joining existing cluster   
         this.token = token;
         cluster.joinCluster(address);
      }
   }

   /**
    * Bootstrap a new cluster by claiming the first id and self-registering
    */
   protected void registerSelf(int myEntityId, long reclaimToken) {
      registry.setParentId(myEntityId);

      this.parentId = this.entityId = myEntityId;
      this.token = EntityToken.encode(entityId, reclaimToken);

      final EntityInfo e = new EntityInfo(entityId, 0, reclaimToken, Util.getHostName(), 0, Core.TYPE_TETRAPOD, getShortName(), 0, 0,
            getContractId());
      registry.register(e);
      logger.info(String.format("SELF-REGISTERED: 0x%08X %s", entityId, e));

      try {
         // Connect to self on localhost 
         clusterClient.connect("localhost", cluster.getLocalPort(), dispatcher).sync();
      } catch (Exception ex) {
         fail(ex);
      }
   }

   @Override
   public void onConnectedToCluster() {
      super.onConnectedToCluster();
      logger.info("Tetrapod connected to Self {}", getEntityId());
      // addMessageHandler(new TetrapodContract.Registry(), registry);
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

      // TODO: wait for confirmed cluster registry sync before calling onReadyToServe
      onReadyToServe();
   }

   /**
    * As a Tetrapod service, we can't start serving as one until we've registered & fully sync'ed with the cluster, or self-registered if we
    * are the first one. We call this once this criteria has been reached
    */
   protected void onReadyToServe() {
      logger.info(" ***** READY TO SERVE ***** ");
      registryTopic = registry.publish(entityId);
      servicesTopic = registry.publish(entityId);

      try {
         publicServer = new Server(properties.optInt("tetrapod.public.port", DEFAULT_PUBLIC_PORT), new TypedSessionFactory(
               Core.TYPE_ANONYMOUS));
         serviceServer = new Server(properties.optInt("tetrapod.service.port", DEFAULT_SERVICE_PORT), new TypedSessionFactory(
               Core.TYPE_SERVICE));
         webSocketsServer = new Server(properties.optInt("tetrapod.websocket.port", DEFAULT_WEBSOCKETS_PORT), new WebSessionFactory(
               "/sockets", true));
         httpServer = new Server(properties.optInt("tetrapod.http.port", DEFAULT_HTTP_PORT), new WebSessionFactory("./webContent", false));

         serviceServer.start().sync();
         publicServer.start().sync();
         webSocketsServer.start().sync();
         httpServer.start().sync();
      } catch (Exception e) {
         fail(e);
      }

      scheduleHealthCheck();
      updateStatus(status & ~Core.STATUS_STARTING);
   }

   @Override
   public void onShutdown(boolean restarting) {
      logger.info("Shutting Down Tetrapod");
      publicServer.stop();
      serviceServer.stop();
      webSocketsServer.stop();
      httpServer.stop();
      cluster.shutdown();
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
                     if (!e.isGone() && e.parentId == getEntityId() || e.isTetrapod()) {
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

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
            // OPTIMIZE: call broadcast() directly instead of through loop-back
            clusterClient.getSession().sendBroadcastMessage(msg, topic.topicId);
         }
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
      if (System.currentTimeMillis() - lastStatsLog > 10 * 1000) {
         registry.logStats();
         lastStatsLog = System.currentTimeMillis();
      }
      for (EntityInfo e : registry.getChildren()) {
         if (e.isGone() && System.currentTimeMillis() - e.getGoneSince() > 60 * 1000) {
            logger.info("Reaping: {}", e);
            registry.unregister(e.entityId);
         }
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public Response requestRegister(RegisterRequest r, RequestContext ctx) {
      if (getEntityId() == 0) {
         return new Error(ERROR_SERVICE_UNAVAILABLE);
      }
      EntityInfo info = null;
      final EntityToken t = EntityToken.decode(r.token);
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

      if (info.type == Core.TYPE_TETRAPOD) {
         info.parentId = info.entityId;
      }

      // update & store session
      ctx.session.setTheirEntityId(info.entityId);
      ctx.session.setTheirEntityType(info.type);

      info.setSession(ctx.session);

      //      if (info.isService()) {
      //         clusterSubscribe(ctx.session, info.entityId); 
      //      }

      return new RegisterResponse(info.entityId, getEntityId(), EntityToken.encode(info.entityId, info.reclaimToken));
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

   private void sendTopicInfo(EntityInfo e, Session session, int toEntityId) {
      for (Topic t : e.getTopics()) {
         session.sendMessage(new TopicPublishedMessage(e.entityId, t.topicId), toEntityId, registryTopic.topicId);
         for (Subscriber s : t.getSubscribers()) {
            session.sendMessage(new TopicSubscribedMessage(t.ownerId, t.topicId, s.entityId), toEntityId, registryTopic.topicId);
         }
      }
   }

   /**
    * Lock registryTopic and send our current registry state to the subscriber
    */
   protected void registrySubscribe(Session session, int toEntityId) {
      if (registryTopic != null) {
         synchronized (registryTopic) {
            broadcastRegistryMessage(new TopicSubscribedMessage(registryTopic.ownerId, registryTopic.topicId, toEntityId));
            // Sends all current entities -- ourselves, and our children
            EntityInfo p = registry.getEntity(getEntityId());
            session.sendMessage(new EntityRegisteredMessage(p), toEntityId, registryTopic.topicId);
            for (EntityInfo e : registry.getChildren()) {
               session.sendMessage(new EntityRegisteredMessage(e), toEntityId, registryTopic.topicId);
            }
            // send topic info
            // OPTIMIZE: could be optimized greatly with custom messages, but this is very simple
            sendTopicInfo(p, session, toEntityId);
            for (EntityInfo e : registry.getChildren()) {
               sendTopicInfo(e, session, toEntityId);
            }
         }
      }
   }

   @Override
   public Response requestRegistrySubscribe(RegistrySubscribeRequest r, RequestContext ctx) {
      if (registryTopic == null) {
         return new Error(ERROR_UNKNOWN);
      }
      registrySubscribe(ctx.session, ctx.header.fromId);
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
            ctx.session.sendMessage(new ServiceAddedMessage(e), ctx.header.fromId, servicesTopic.topicId);
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

   @Override
   public Response requestJoinCluster(JoinClusterRequest r, RequestContext ctx) {
      if (ctx.session.getTheirEntityType() != Core.TYPE_TETRAPOD) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      if (ctx.session.getTheirEntityId() != 0) {
         return new Error(ERROR_INVALID_ENTITY);
      }

      ctx.session.sendRequest(new RegistrySubscribeRequest(), Core.UNADDRESSED);
      registrySubscribe(ctx.session, ctx.session.getTheirEntityId());

      return new JoinClusterResponse(getEntityId());
   }

}
