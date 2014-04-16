package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.UNADDRESSED;
import static io.tetrapod.protocol.core.TetrapodContract.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.Session.RelayHandler;
import io.tetrapod.core.registry.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.StructureAdapter;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;
import io.tetrapod.core.utils.*;
import io.tetrapod.core.web.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.service.ServiceCommand;
import io.tetrapod.protocol.storage.*;

import java.io.*;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.*;

/**
 * The tetrapod service is the core cluster service which handles message routing, cluster management, service discovery, and load balancing
 * of client connections
 */
public class TetrapodService extends DefaultService implements TetrapodContract.API, StorageContract.API, RelayHandler,
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
   private final TetrapodWorker                       worker;

   private Storage                                    storage;

   private Server                                     serviceServer;
   private Server                                     publicServer;
   private Server                                     webSocketsServer;
   private Server                                     httpServer;

   private final WebRoutes                            webRoutes               = new WebRoutes();

   @Deprecated
   // FIXME: Decide how we want to manage properties & configuration
   private final Properties                           properties              = new Properties();

   private long                                       lastStatsLog;
   private String                                     webContentRoot;

   public TetrapodService() {
      // Load properties for override
      for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
         if (e.getKey().toString().startsWith("tetrapod.")) {
            properties.put(e.getKey().toString(), e.getValue().toString());
         }
      }
      registry = new io.tetrapod.core.registry.Registry(this);
      worker = new TetrapodWorker(this);
      cluster = new TetrapodCluster(this, properties);
      setMainContract(new TetrapodContract());
      addContracts(new StorageContract());

      addSubscriptionHandler(new TetrapodContract.Registry(), registry);
   }

   @Override
   public void startNetwork(ServerAddress address, String token, Map<String, String> otherOpts) throws Exception {
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
      webContentRoot = "./webContent";
      if (otherOpts.containsKey("webroot"))
         webContentRoot = otherOpts.get("webroot");
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

      clusterTopic = registry.publish(entityId);
      registryTopic = registry.publish(entityId);
      servicesTopic = registry.publish(entityId);
      try {
         // Establish a special loopback connection to ourselves
         // connects to self on localhost on our clusterport
         clusterClient.connect("localhost", getClusterPort(), dispatcher).sync();
      } catch (Exception ex) {
         fail(ex);
      }
   }

   @Override
   public String getServiceIcon() {
      return "media/lizard.png";
   }

   @Override
   public ServiceCommand[] getServiceCommands() {
      return new ServiceCommand[] { new ServiceCommand("Log Registry Stats", null, LogRegistryStatsRequest.CONTRACT_ID,
            LogRegistryStatsRequest.STRUCT_ID) };
   }

   public byte getEntityType() {
      return Core.TYPE_TETRAPOD;
   }

   public int getServicePort() {
      return properties.optInt("tetrapod.service.port", DEFAULT_SERVICE_PORT);
   }

   public int getClusterPort() {
      return properties.optInt("tetrapod.cluster.port", DEFAULT_CLUSTER_PORT);
   }

   public int getPublicPort() {
      return properties.optInt("tetrapod.public.port", DEFAULT_PUBLIC_PORT);
   }

   public int getWebSocketPort() {
      return properties.optInt("tetrapod.websocket.port", DEFAULT_WEBSOCKETS_PORT);
   }

   public int getHTTPPort() {
      return properties.optInt("tetrapod.http.port", DEFAULT_HTTP_PORT);
   }

   @Override
   public long getCounter() {
      return serviceServer.getNumSessions() + publicServer.getNumSessions() + webSocketsServer.getNumSessions()
            + httpServer.getNumSessions() + cluster.getNumSessions();
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
               onEntityDisconnected(ses);
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
               onEntityDisconnected(ses);
            }

            @Override
            public void onSessionStart(Session ses) {}
         });
         return ses;
      }
   }

   protected void onEntityDisconnected(Session ses) {
      if (ses.getTheirEntityId() != 0) {
         final EntityInfo e = registry.getEntity(ses.getTheirEntityId());
         if (e != null) {
            registry.setGone(e);
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

      try {
         storage = new Storage();
         publicServer = new Server(getPublicPort(), new TypedSessionFactory(Core.TYPE_ANONYMOUS), dispatcher);
         serviceServer = new Server(getServicePort(), new TypedSessionFactory(Core.TYPE_SERVICE), dispatcher);
         webSocketsServer = new Server(getWebSocketPort(), new WebSessionFactory("/sockets", true), dispatcher);
         httpServer = new Server(getHTTPPort(), new WebSessionFactory(webContentRoot, false), dispatcher);

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
      if (cluster != null) {
         cluster.shutdown();
      }
      if (publicServer != null) {
         publicServer.stop();
      }
      if (serviceServer != null) {
         serviceServer.stop();
      }
      if (webSocketsServer != null) {
         webSocketsServer.stop();
      }
      if (httpServer != null) {
         httpServer.stop();
      }
      if (storage != null) {
         storage.shutdown();
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   private Session findSession(final EntityInfo entity) {
      if (entity.parentId == getEntityId()) {
         return entity.getSession();
      } else {
         if (entity.isTetrapod()) {
            return cluster.getSession(entity.entityId);
         }
         final EntityInfo parent = registry.getEntity(entity.parentId);
         assert (parent != null);
         return cluster.getSession(parent.entityId);
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
   public void relayMessage(final MessageHeader header, final ByteBuf buf, final boolean isBroadcast) throws IOException {
      final EntityInfo sender = registry.getEntity(header.fromId);
      if (sender != null) {
         buf.retain();
         sender.queue(new Runnable() {
            public void run() {
               try {
                  if (header.toId == UNADDRESSED) {
                     if (isBroadcast) {
                        broadcast(sender, header, buf);
                     }
                  } else {
                     final Session ses = getRelaySession(header.toId, header.contractId);
                     if (ses != null) {
                        ses.sendRelayedMessage(header, buf, false);
                     }
                  }
               } catch (Throwable e) {
                  logger.error(e.getMessage(), e);
               } finally {
                  // FIXME: This is fragile -- if we delete an entity with queued work, we need to make sure we 
                  // release all the buffers in the queued work items.
                  buf.release();
               }
            }
         });
         worker.kick();
      } else {
         logger.error("Could not find sender entity {} for {}", header.fromId, header.dump());
      }
   }

   private void broadcast(final EntityInfo publisher, final MessageHeader header, final ByteBuf buf) throws IOException {
      final Topic topic = publisher.getTopic(header.topicId);
      if (topic != null) {
         for (final Subscriber s : topic.getChildSubscribers()) {
            broadcast(publisher, s, topic, header, buf);
         }
         for (final Subscriber s : topic.getProxySubscribers()) {
            broadcast(publisher, s, topic, header, buf);
         }
      } else {
         logger.error("Could not find topic {} for entity {}", header.topicId, publisher);
      }
   }

   private void broadcast(final EntityInfo publisher, final Subscriber sub, final Topic topic, final MessageHeader header, final ByteBuf buf)
         throws IOException {
      final int ri = buf.readerIndex();
      final EntityInfo e = registry.getEntity(sub.entityId);
      if (e != null) {
         if (e.entityId == getEntityId()) {
            // dispatch to self
            ByteBufDataSource reader = new ByteBufDataSource(buf);
            final Message msg = (Message) StructureFactory.make(header.contractId, header.structId);
            if (msg != null) {
               msg.read(reader);
               clusterClient.getSession().dispatchMessage(header, msg);
            }
            buf.readerIndex(ri);
         } else {
            if (!e.isGone() && (e.parentId == getEntityId() || e.isTetrapod())) {
               final Session session = findSession(e);
               if (session != null) {
                  // rebroadcast this message if it was published by one of our children and we're sending it to another tetrapod
                  final boolean keepBroadcasting = e.isTetrapod() && publisher.parentId == getEntityId();
                  session.sendRelayedMessage(header, buf, keepBroadcasting);
                  buf.readerIndex(ri);
               } else {
                  logger.error("Could not find session for {} {}", e, header.dump());
               }
            }
         }
      } else {
         logger.error("Could not find subscriber {} for topic {}", sub, topic);
      }
   }

   @Override
   public WebRoutes getWebRoutes() {
      return webRoutes;
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public void broadcastRegistryMessage(Message msg) {
      if (registryTopic.getNumScubscribers() > 0) {
         broadcast(msg, registryTopic);
      }
      cluster.broadcast(msg);
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

   @Override
   public void subscribe(int topicId, int entityId) {
      registry.subscribe(registry.getEntity(getEntityId()), topicId, entityId);
   }

   @Override
   public void unsubscribe(int topicId, int entityId) {
      registry.unsubscribe(registry.getEntity(getEntityId()), topicId, entityId, false);
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   private void scheduleHealthCheck() {
      dispatcher.dispatch(1, TimeUnit.SECONDS, new Runnable() {
         public void run() {
            if (dispatcher.isRunning()) {
               try {
                  healthCheck();
                  cluster.service();
               } catch (Throwable e) {
                  logger.error(e.getMessage(), e);
               }
               scheduleHealthCheck();
            }
         }
      });
   }

   private void healthCheck() {
      if (System.currentTimeMillis() - lastStatsLog > 5 * 60 * 1000) {
         registry.logStats();
         lastStatsLog = System.currentTimeMillis();
      }
      for (EntityInfo e : registry.getChildren()) {
         if (e.isGone() && System.currentTimeMillis() - e.getGoneSince() > 60 * 1000) {
            logger.info("Reaping: {}", e);
            registry.unregister(e);
         }
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   private void subscribeToCluster(Session ses, int toEntityId) {
      assert (clusterTopic != null);
      synchronized (cluster) {
         subscribe(clusterTopic.topicId, toEntityId);
         cluster.sendClusterDetails(ses, toEntityId, clusterTopic.topicId);
      }
   }

   @Override
   public void messageClusterMember(ClusterMemberMessage m, MessageContext ctx) {
      synchronized (cluster) {
         if (cluster.addMember(m.entityId, m.host, m.servicePort, m.clusterPort, null)) {
            broadcast(new ClusterMemberMessage(m.entityId, m.host, m.servicePort, m.clusterPort), clusterTopic);
         }
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public Response requestRegister(RegisterRequest r, final RequestContext ctx) {
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

      // deliver them their entityId immediately to avoid some race conditions with the response
      ctx.session.sendMessage(new EntityMessage(info.entityId), Core.UNADDRESSED, Core.UNADDRESSED);

      if (info.isService() && info.entityId != entityId) {
         subscribeToCluster(ctx.session, info.entityId);
      }

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
      registry.unregister(info);
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

   /**
    * Lock registryTopic and send our current registry state to the subscriber
    */
   protected void registrySubscribe(final Session session, final int toEntityId, boolean clusterMode) {
      if (registryTopic != null) {
         synchronized (registryTopic) {
            // cluster members are not subscribed through this subscription, due to chicken-and-egg issues
            // synchronizing registries using topics. Cluster members are implicitly auto-subscribed without
            // an entry in the topic.
            if (!clusterMode) {
               subscribe(registryTopic.topicId, toEntityId);
            }
            registry.sendRegistryState(session, toEntityId, registryTopic.topicId);
         }
      }
   }

   @Override
   public Response requestRegistrySubscribe(RegistrySubscribeRequest r, RequestContext ctx) {
      if (registryTopic == null) {
         return new Error(ERROR_UNKNOWN);
      }
      registrySubscribe(ctx.session, ctx.header.fromId, false);
      return Response.SUCCESS;
   }

   @Override
   public Response requestRegistryUnsubscribe(RegistryUnsubscribeRequest r, RequestContext ctx) {
      // TODO: validate  
      unsubscribe(registryTopic.topicId, ctx.header.fromId);
      return Response.SUCCESS;
   }

   @Override
   public Response requestServicesSubscribe(ServicesSubscribeRequest r, RequestContext ctx) {
      if (servicesTopic == null) {
         return new Error(ERROR_UNKNOWN);
      }
      synchronized (servicesTopic) {
         subscribe(servicesTopic.topicId, ctx.header.fromId);
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
      unsubscribe(servicesTopic.topicId, ctx.header.fromId);
      return Response.SUCCESS;
   }

   @Override
   public Response requestServiceStatusUpdate(ServiceStatusUpdateRequest r, RequestContext ctx) {
      // TODO: don't allow certain bits to be set from a request
      if (ctx.header.fromId != 0) {
         final EntityInfo e = registry.getEntity(ctx.header.fromId);
         if (e != null) {
            registry.updateStatus(e, r.status);
         } else {
            return new Error(ERROR_INVALID_ENTITY);
         }
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
   protected void registerServiceInformation() {
      // do nothing, our protocol is known by all tetrapods
   }

   @Override
   public Response requestClusterJoin(ClusterJoinRequest r, RequestContext ctx) {
      if (ctx.session.getTheirEntityType() != Core.TYPE_TETRAPOD) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      ctx.session.setTheirEntityId(r.entityId);

      logger.info("JOINING TETRAPOD {} {}", ctx.session);

      synchronized (cluster) {
         if (cluster.addMember(r.entityId, r.host, r.servicePort, r.clusterPort, ctx.session)) {
            broadcast(new ClusterMemberMessage(r.entityId, r.host, r.servicePort, r.clusterPort), clusterTopic);
         }
      }

      registrySubscribe(ctx.session, ctx.session.getTheirEntityId(), true);

      return new ClusterJoinResponse(getEntityId());
   }

   @Override
   public Response requestLogRegistryStats(LogRegistryStatsRequest r, RequestContext ctx) {
      registry.logStats();
      Util.random(cluster.getMembers()).getSession().close();
      return Response.SUCCESS;
   }

   @Override
   public Response requestStorageGet(StorageGetRequest r, RequestContext ctx) {
      return new StorageGetResponse(storage.get(r.key));
   }

   @Override
   public Response requestStorageSet(StorageSetRequest r, RequestContext ctx) {
      storage.put(r.key, r.value);
      return Response.SUCCESS;
   }

   @Override
   public Response requestStorageDelete(StorageDeleteRequest r, RequestContext ctx) {
      storage.delete(r.key);
      return Response.SUCCESS;
   }

}
