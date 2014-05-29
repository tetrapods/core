package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.*;
import static io.tetrapod.protocol.core.CoreContract.*;
import static io.tetrapod.protocol.core.TetrapodContract.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.AdminAccounts.AdminMutator;
import io.tetrapod.core.Session.RelayHandler;
import io.tetrapod.core.flashpolicy.FlashPolicyServer;
import io.tetrapod.core.registry.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.StructureAdapter;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;
import io.tetrapod.core.utils.*;
import io.tetrapod.core.web.*;
import io.tetrapod.core.web.WebRoot.FileResult;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.storage.*;

import java.io.*;
import java.net.ConnectException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import javax.net.ssl.SSLContext;

import org.slf4j.*;

/**
 * The tetrapod service is the core cluster service which handles message routing, cluster management, service discovery, and load balancing
 * of client connections
 */
public class TetrapodService extends DefaultService implements TetrapodContract.API, StorageContract.API, RelayHandler,
      io.tetrapod.core.registry.Registry.RegistryBroadcaster {

   public static final Logger                         logger      = LoggerFactory.getLogger(TetrapodService.class);

   protected final SecureRandom                       random      = new SecureRandom();

   protected final io.tetrapod.core.registry.Registry registry;

   private Topic                                      clusterTopic;
   private Topic                                      registryTopic;
   private Topic                                      servicesTopic;

   private final TetrapodCluster                      cluster;
   private final TetrapodWorker                       worker;

   private Storage                                    storage;
   private AdminAccounts                              adminAccounts;

   private final EventLoopGroup                       bossGroup   = new NioEventLoopGroup();
   private final List<Server>                         servers     = new ArrayList<Server>();

   private final WebRoutes                            webRoutes   = new WebRoutes();

   private long                                       lastStatsLog;
   private ConcurrentMap<String, WebRoot>             webRootDirs = new ConcurrentHashMap<>();

   public TetrapodService() {
      registry = new io.tetrapod.core.registry.Registry(this);
      worker = new TetrapodWorker(this);
      cluster = new TetrapodCluster(this);
      setMainContract(new TetrapodContract());
      addContracts(new StorageContract());
      addSubscriptionHandler(new TetrapodContract.Registry(), registry);
   }

   @Override
   public void startNetwork(ServerAddress address, String token, Map<String, String> otherOpts) throws Exception {
      logger.info("***** Start Network ***** ");
      logger.info("Joining Cluster: {}", address.dump());
      if (address.host.equals("self")) {
         cluster.startListening();
         registerSelf(io.tetrapod.core.registry.Registry.BOOTSTRAP_ID, random.nextLong());
      } else if (address.host.equals("auto")) {
         boolean joined = false;
         for (String host : getAllVPCMembers()) {
            logger.info("Trying to join cluster on {}", host);
            address.host = host;
            this.token = token;
            try {
               joined = cluster.joinCluster(address);
            } catch (ConnectException e) {}
            if (joined)
               break;
         }
         // need to start listener after the auto-search but before the self reg
         cluster.startListening();
         if (!joined) {
            logger.info("no tetrapods found, registering self");
            registerSelf(io.tetrapod.core.registry.Registry.BOOTSTRAP_ID, random.nextLong());
         }
      } else {
         cluster.startListening();
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

      final EntityInfo e = new EntityInfo(entityId, 0, reclaimToken, Util.getHostName(), 0, Core.TYPE_TETRAPOD, getShortName(),
            buildNumber, 0, getContractId());
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

   /**
    * We need to override the connectToCluster in superclass because that one tries to reconnect to other tetrapods, but the clusterClient
    * connection is a special loopback connection in the tetrapod, so we should only ever reconnect back to ourselves.
    */
   @Override
   protected void connectToCluster(int retrySeconds) {
      if (!isShuttingDown() && !clusterClient.isConnected()) {
         try {
            clusterClient.connect("localhost", getClusterPort(), dispatcher).sync();
         } catch (Exception ex) {
            // something is seriously awry if we cannot connect to ourselves
            fail(ex);
         }
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
      return Util.getProperty("tetrapod.service.port", DEFAULT_SERVICE_PORT);
   }

   public int getClusterPort() {
      return Util.getProperty("tetrapod.cluster.port", DEFAULT_CLUSTER_PORT);
   }

   public int getPublicPort() {
      return Util.getProperty("tetrapod.public.port", DEFAULT_PUBLIC_PORT);
   }

   public int getHTTPPort() {
      return Util.getProperty("tetrapod.http.port", DEFAULT_HTTP_PORT);
   }

   public int getHTTPSPort() {
      return Util.getProperty("tetrapod.https.port", DEFAULT_HTTPS_PORT);
   }

   @Override
   public long getCounter() {
      long count = cluster.getNumSessions();
      for (Server s : servers) {
         count += s.getNumSessions();
      }
      return count;
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
      public WebSessionFactory(Map<String, WebRoot> contentRootMap, String webSockets) {
         this.webSockets = webSockets;
         this.contentRootMap = contentRootMap;
      }

      final String               webSockets;
      final Map<String, WebRoot> contentRootMap;

      @Override
      public Session makeSession(SocketChannel ch) {
         TetrapodService pod = TetrapodService.this;
         Session ses = null;
         ses = new WebSocketSession(ch, pod, contentRootMap, webSockets);
         ses.setRelayHandler(pod);
         ses.setMyEntityId(getEntityId());
         ses.setMyEntityType(Core.TYPE_TETRAPOD);
         ses.setTheirEntityType(Core.TYPE_CLIENT);
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

   /**
    * As a Tetrapod service, we can't start serving as one until we've registered & fully sync'ed with the cluster, or self-registered if we
    * are the first one. We call this once this criteria has been reached
    */
   @Override
   public void onReadyToServe() {
      if ((getStatus() & Core.STATUS_STARTING) != 0) {
         // TODO: wait for confirmed cluster registry sync before calling onReadyToServe
         logger.info(" ***** READY TO SERVE ***** ");

         try {
            storage = new Storage();
            registry.setStorage(storage);
            AuthToken.setSecret(storage.getSharedSecret());
            adminAccounts = new AdminAccounts(storage);

            // create servers
            servers.add(new Server(getPublicPort(), new TypedSessionFactory(Core.TYPE_ANONYMOUS), dispatcher));
            servers.add(new Server(getServicePort(), new TypedSessionFactory(Core.TYPE_SERVICE), dispatcher));
            servers.add(new Server(getHTTPPort(), new WebSessionFactory(webRootDirs, "/sockets"), dispatcher));
            servers.add(new FlashPolicyServer(dispatcher));

            // create secure port servers, if configured
            if (Util.getProperty("tetrapod.tls", true)) {
               SSLContext ctx = Util.createSSLContext(new FileInputStream(Util.getProperty("tetrapod.jks.file", "cfg/tetrapod.jks")),
                     System.getProperty("tetrapod.jks.pwd", "4pod.dop4").toCharArray());
               servers.add(new Server(getHTTPSPort(), new WebSessionFactory(webRootDirs, "/sockets"), dispatcher, ctx, false));
            }

            // start listening
            for (Server s : servers) {
               s.start(bossGroup).sync();
            }
         } catch (Exception e) {
            fail(e);
         }

         scheduleHealthCheck();
      }
   }

   @Override
   public void onShutdown(boolean restarting) {
      logger.info("Shutting Down Tetrapod");
      if (cluster != null) {
         cluster.shutdown();
      }
      try {
         // we have one boss group for all the other servers
         bossGroup.shutdownGracefully().sync();
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
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
            logger.debug("Could not find an entity for {}", entityId);
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
         synchronized (topic) {
            for (final Subscriber s : topic.getChildSubscribers()) {
               broadcast(publisher, s, topic, header, buf);
            }
            for (final Subscriber s : topic.getProxySubscribers()) {
               broadcast(publisher, s, topic, header, buf);
            }
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
            Session ses = clusterClient.getSession();
            if (ses != null) {
               ses.sendBroadcastMessage(msg, topic.topicId);
            } else {
               logger.error("broadcast failed: no session for loopback connection");
            }
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
   public Response requestKeepAlive(KeepAliveRequest r, RequestContext ctx) {
      return Response.SUCCESS;
   }

   @Override
   public Response requestRegister(RegisterRequest r, final RequestContext ctxA) {
      SessionRequestContext ctx = (SessionRequestContext) ctxA;
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
         info.host = r.host;
         info.name = r.name;
         info.reclaimToken = random.nextLong();
         info.contractId = r.contractId;
      }

      info.status = r.status &= ~Core.STATUS_GONE;
      info.parentId = getEntityId();
      info.type = ctx.session.getTheirEntityType();
      if (info.type == Core.TYPE_ANONYMOUS) {
         info.type = Core.TYPE_CLIENT;
         // clobber their reported host with their IP 
         info.host = ctx.session.getPeerHostname();
      }

      // register/reclaim
      registry.register(info);

      logger.info("Registering: {} type={}", info, info.type);

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
               int[] topicIds = new int[r.numTopics];
               for (int i = 0; i < topicIds.length; i++) {
                  final Topic t = registry.publish(ctx.header.fromId);
                  if (t == null) {
                     topicIds = null;
                     break;
                  }
                  topicIds[i] = t.topicId;
               }
               if (topicIds != null) {
                  return new PublishResponse(topicIds);
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
   public Response requestRegistrySubscribe(RegistrySubscribeRequest r, RequestContext ctxA) {
      SessionRequestContext ctx = (SessionRequestContext) ctxA;
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
   public Response requestServicesSubscribe(ServicesSubscribeRequest r, RequestContext ctxA) {
      SessionRequestContext ctx = (SessionRequestContext) ctxA;
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
   public Response requestClusterJoin(ClusterJoinRequest r, RequestContext ctxA) {
      SessionRequestContext ctx = (SessionRequestContext) ctxA;
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

   @Override
   public Response requestAdminAuthorize(AdminAuthorizeRequest r, RequestContext ctxA) {
      SessionRequestContext ctx = (SessionRequestContext) ctxA;
      logger.debug("AUTHORIZE WITH {} ...", r.token);
      AuthToken.Decoded d = AuthToken.decodeAuthToken1(r.token);
      if (d != null) {
         logger.debug("TOKEN {} time left = {}", r.token, d.timeLeft);
         ctx.session.theirType = Core.TYPE_ADMIN;
         return Response.SUCCESS;
      } else {
         logger.warn("TOKEN {} NOT VALID", r.token);
      }
      return new Error(ERROR_INVALID_RIGHTS);
   }

   @Override
   public Response requestAdminLogin(AdminLoginRequest r, RequestContext ctxA) {
      SessionRequestContext ctx = (SessionRequestContext) ctxA;
      if (r.email == null) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      try {
         Admin admin = adminAccounts.getAdminByEmail(r.email);
         if (admin != null) {
            if (adminAccounts.recordLoginAttempt(admin)) {
               return new Error(ERROR_INVALID_CREDENTIALS); // prevent brute force attack
            }
            if (PasswordHash.validatePassword(r.password, admin.hash)) {
               // mark the session as an admin
               ctx.session.theirType = Core.TYPE_ADMIN;
               final String authtoken = AuthToken.encodeAuthToken1(admin.accountId, 0, 60 * 24 * 14);
               return new AdminLoginResponse(authtoken);
            } else {
               return new Error(ERROR_INVALID_CREDENTIALS); // invalid password
            }
         } else {
            return new Error(ERROR_INVALID_CREDENTIALS); // invalid account
         }
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
         return new Error(ERROR_UNKNOWN);
      }
   }

   @Override
   public Response requestAdminChangePassword(final AdminChangePasswordRequest r, RequestContext ctxA) {
      if (ctxA.header.fromType != TYPE_ADMIN) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      // TODO: validate they are already logged in as an admin
      AuthToken.Decoded d = AuthToken.decodeAuthToken1(r.token);
      if (d != null) {
         Admin admin = adminAccounts.getAdminByAccountId(d.accountId);
         if (admin != null) {
            try {
               if (PasswordHash.validatePassword(r.oldPassword, admin.hash)) {
                  final String newHash = PasswordHash.createHash(r.newPassword);
                  admin = adminAccounts.mutate(admin, new AdminMutator() {
                     @Override
                     public void mutate(Admin admin) {
                        admin.hash = newHash;
                     }
                  });
                  if (admin != null) {
                     return Response.SUCCESS;
                  }
               } else {
                  return new Error(ERROR_INVALID_CREDENTIALS);
               }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
               logger.error(e.getMessage(), e);
            }
         }
      } else {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      return new Error(ERROR_UNKNOWN);
   }

   @Override
   public Response requestAdminChangeRights(AdminChangeRightsRequest r, RequestContext ctx) {
      // TODO: Implement;
      return new Error(ERROR_UNKNOWN);
   }

   @Override
   public Response requestAdminCreate(AdminCreateRequest r, RequestContext ctx) {
      // TODO: Implement;
      return new Error(ERROR_UNKNOWN);
   }

   @Override
   public Response requestAdminDelete(AdminDeleteRequest r, RequestContext ctx) {
      // TODO: Implement;
      return new Error(ERROR_UNKNOWN);
   }

   @Override
   public Response requestAddWebFile(AddWebFileRequest r, RequestContext ctx) {
      WebRoot root = null;
      root = webRootDirs.get(r.webRootName);
      if (root == null) {
         webRootDirs.putIfAbsent(r.webRootName, r.contents == null ? new WebRootLocalFilesystem() : new WebRootInMemory());
         root = webRootDirs.get(r.webRootName);
      }
      if (r.clearBeforAdding)
         root.clear();
      root.addFile(r.path, r.contents);
      if (logger.isDebugEnabled()) {
         int size = 0;
         for (WebRoot roo : webRootDirs.values()) {
            size += roo.getMemoryFootprint();
         }
         logger.debug("Total web footprint is {} MBs", ((double) size / (double) (1024 * 1024)));
      }
      return Response.SUCCESS;
   }

   @Override
   public Response requestSendWebRoot(SendWebRootRequest r, RequestContext ctx) {
      WebRoot root = webRootDirs.get(r.webRootName);
      boolean first = true;
      for (String path : root.getAllPaths()) {
         try {
            FileResult res;
            res = root.getFile(path);
            sendRequest(new AddWebFileRequest(path, r.webRootName, res.contents, first), ctx.header.fromId);
            first = false;
         } catch (IOException e) {
            logger.error("trouble sedning root", e);
         }
      }
      return Response.SUCCESS;
   }

   protected File[] getDevProtocolWebRoots() {
      return new File[] { new File("../Protocol-Tetrapod/rsc"), new File("../Protocol-Core/rsc") };
   }

   //------------ building

   @Override
   public Response requestGetServiceBuildInfo(GetServiceBuildInfoRequest r, RequestContext ctx) {
      return new GetServiceBuildInfoResponse(Builder.getServiceInfo());
   }

   @Override
   public Response requestExecuteBuildCommand(ExecuteBuildCommandRequest r, RequestContext ctx) {
      for (BuildCommand command : r.commands) {
         boolean success = Builder.executeCommand(command, this);
         if (!success)
            return Response.error(ERROR_UNKNOWN);
      }
      return Response.SUCCESS;
   }

   private Set<String> getAllVPCMembers() {
      // assume /etc/hosts has all members listed as services##
      Set<String> res = new HashSet<>();
      try {
         for (String line : Files.readAllLines(new File("/etc/hosts").toPath(), Charset.forName("UTF-8"))) {
            Matcher m = Pattern.compile(".*(services\\d\\d).*").matcher(line);
            if (m.matches()) {
               res.add(m.group(1));
            }
         }
      } catch (IOException e) {
         logger.error("could not read /etc/hosts", e);
      }
      return res;
   }

}
