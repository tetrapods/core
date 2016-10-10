package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.*;
import static io.tetrapod.protocol.core.CoreContract.*;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.ServiceConnector.DirectServiceInfo;
import io.tetrapod.core.Session.RelayHandler;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.core.pubsub.Topic;
import io.tetrapod.core.registry.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.storage.*;
import io.tetrapod.core.tasks.TaskContext;
import io.tetrapod.core.utils.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.raft.*;
import io.tetrapod.protocol.storage.*;
import io.tetrapod.protocol.web.WebContract;

/**
 * The tetrapod service is the core cluster service which handles message routing, cluster management, service discovery, and load balancing
 * of client connections
 */
public class TetrapodService extends DefaultService
      implements TetrapodContract.API, StorageContract.API, RaftContract.API, RelayHandler, EntityRegistry.RegistryBroadcaster {

   public static final Logger      logger        = LoggerFactory.getLogger(TetrapodService.class);
   public static final Logger      auditLogger   = LoggerFactory.getLogger("audit");

   public final SecureRandom       random        = new SecureRandom();

   private final Topic             clusterTopic  = publishTopic();
   private final Topic             servicesTopic = publishTopic();
   private final Topic             adminTopic    = publishTopic();

   private final TetrapodWorker    worker;

   protected final TetrapodCluster cluster       = new TetrapodCluster(this);

   public final EntityRegistry     registry      = new EntityRegistry(this, cluster);

   private AdminAccounts           adminAccounts;

   private final List<Server>      servers       = new ArrayList<>();

   private long                    lastStatsLog;

   public TetrapodService() throws IOException {
      super(new TetrapodContract());

      worker = new TetrapodWorker(this);
      addContracts(new StorageContract());
      addContracts(new RaftContract());

      // add tetrapod web routes
      for (WebRoute r : contract.getWebRoutes()) {
         getWebRoutes().setRoute(r.path, r.contractId, r.subContractId, r.structId);
      }
   }

   @Override
   public void startNetwork(ServerAddress address, String token, Map<String, String> otherOpts, Launcher launcher) throws Exception {
      logger.info("***** Start Network ***** ");
      logger.info("Joining Cluster: {} {}", address.dump(), otherOpts);
      this.startPaused = otherOpts.get("paused").equals("true");
      this.token = token;
      cluster.init();
      scheduleHealthCheck();
   }

   /**
    * Bootstrap a new cluster by claiming the first id and self-registering
    */
   public void registerSelf(int myEntityId, long reclaimToken) {
      try {
         registry.setParentId(myEntityId);

         this.parentId = this.entityId = myEntityId;
         this.token = EntityToken.encode(entityId, reclaimToken);

         EntityInfo me = cluster.getEntity(entityId);
         if (me == null) {
            throw new RuntimeException("Not in registry");
         } else {
            this.token = EntityToken.encode(entityId, me.reclaimToken);
            logger.info(String.format("SELF-REGISTERED: 0x%08X %s", entityId, me));
            me.status = getStatus(); // update status?
            me.build = buildName;
         }

         adminTopic.addListener((toEntityId, toChildId, resub) -> {
            synchronized (cluster) {
               Session ses = findSession(toEntityId);
               if (ses != null) {
                  cluster.sendAdminDetails(ses, toEntityId, toChildId, adminTopic.topicId);
                  for (String host : nagiosEnabled.keySet()) {
                     ses.sendMessage(new NagiosStatusMessage(host, nagiosEnabled.get(host)), toEntityId, toChildId);
                  }
               }
            }
         });

         // Establish a special loopback connection to ourselves
         // connects to self on localhost on our clusterport
         clusterClient.connect("localhost", getClusterPort(), dispatcher).sync();
      } catch (Exception ex) {
         fail(ex);
      }
   }

   public boolean dependenciesReady() {
      return cluster.isReady();
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
            LogRegistryStatsRequest.STRUCT_ID, false), };
   }

   @Override
   public byte getEntityType() {
      return Core.TYPE_TETRAPOD;
   }

   public int getServicePort() {
      return Util.getProperty("tetrapod.service.port", DEFAULT_SERVICE_PORT);
   }

   public int getClusterPort() {
      return Util.getProperty("tetrapod.cluster.port", DEFAULT_CLUSTER_PORT);
   }

   @Override
   public long getCounter() {
      return cluster.getNumSessions() + servers.stream().mapToInt(Server::getNumSessions).sum();
   }

   private class TypedSessionFactory extends WireSessionFactory {

      private TypedSessionFactory(byte type) {
         super(TetrapodService.this, type, new Session.Listener() {
            @Override
            public void onSessionStop(Session ses) {
               onEntityDisconnected(ses);
            }

            @Override
            public void onSessionStart(Session ses) {}
         });
      }

      /**
       * Session factory for our sessions from clients and services
       */
      @Override
      public Session makeSession(SocketChannel ch) {
         final Session ses = super.makeSession(ch);
         ses.setName("T" + ses.theirType);
         ses.setRelayHandler(TetrapodService.this);
         return ses;
      }
   }

   public void onEntityDisconnected(Session ses) {
      logger.info("Session Stopped: {}", ses);
      if (ses.getTheirEntityId() != 0) {
         final EntityInfo e = registry.getEntity(ses.getTheirEntityId());
         if (e != null) {
            if (!e.isService() || cluster.isLeader()) {
               registry.setGone(e);
            }
         }
      }
   }

   /**
    * As a Tetrapod service, we can't start serving as one until we've registered & fully sync'ed with the cluster, or self-registered if we
    * are the first one. We call this once this criteria has been reached
    */
   @Override
   public void onReadyToServe() {
      logger.info(" ***** READY TO SERVE ***** ");
      if (isStartingUp()) {
         try {
            AdminAuthToken.setSecret(getSharedSecret());
            adminAccounts = new AdminAccounts(cluster);

            servers.add(new Server(getServicePort(), new TypedSessionFactory(Core.TYPE_SERVICE), dispatcher, sslContext, false));

            // start listening
            for (Server s : servers) {
               s.start().sync();
            }
         } catch (Exception e) {
            fail(e);
         }
      }
   }

   @Override
   public void onShutdown(boolean restarting) {
      logger.info("Shutting Down Tetrapod");
      if (!Util.isLocal()) {
         logger.info("Sleeping ....");
         // sleep a bit so other services getting a kill signal can shutdown cleanly
         Util.sleep(2500);
      }
      if (cluster != null) {
         cluster.shutdown();
      }
   }

   /**
    * Extract a shared secret key for seeding server HMACs
    */
   public String getSharedSecret() {
      String secret = Util.getProperty(AdminAuthToken.SHARED_SECRET_KEY);
      if (secret == null) {
         secret = AuthToken.generateSharedSecret();
         cluster.setClusterProperty(new ClusterProperty(AdminAuthToken.SHARED_SECRET_KEY, true, secret));
      }
      return secret;
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   private Session findSession(final int entityId) {
      EntityInfo e = registry.getEntity(entityId);
      if (e != null) {
         return findSession(e);
      }
      return null;
   }

   private Session findSession(final EntityInfo entity) {
      if (entity.parentId == getEntityId()) {
         return entity.getSession();
      } else {
         if (entity.isTetrapod()) {
            return cluster.getSession(entity.entityId);
         }
         final EntityInfo parent = registry.getEntity(entity.parentId);
         if (parent != null) {
            assert (parent != null);
            return cluster.getSession(parent.entityId);
         } else {
            logger.warn("Could not find parent entity {} for {}", entity.parentId, entity);
            return null;
         }
      }
   }

   @Override
   public int getAvailableService(int contractId) {
      final EntityInfo entity = registry.getRandomAvailableService(contractId);
      if (entity != null) {
         return entity.entityId;
      }
      return 0;
   }

   @Override
   public boolean isServiceExistant(int entityId) {
      EntityInfo info = registry.getEntity(entityId);
      if (info != null) {
         return info.isService();
      }
      return false;
   }

   @Override
   public Session getRelaySession(int entityId, int contractId) {
      EntityInfo entity = null;
      if (entityId == Core.UNADDRESSED) {
         entity = registry.getRandomAvailableService(contractId);
      } else {
         entity = registry.getEntity(entityId);
         if (entity == null) {
            int parentEntity = entityId & TetrapodContract.PARENT_ID_MASK;
            if (parentEntity != entityId && parentEntity != getEntityId()) {
               return getRelaySession(parentEntity, contractId);
            } else {
               logger.debug("Could not find an entity for {}", entityId);
            }
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
         sender.queue(() -> {
            try {
               if ((header.flags & MessageHeader.FLAGS_ALTERNATE) != 0) {
                  // relay to all web services:
                  final int ri = buf.readerIndex();
                  for (EntityInfo e : registry.getServicesList(WebContract.CONTRACT_ID)) {
                     if (e.hasConnectedSession()) {
                        e.getSession().sendRelayedMessage(header, buf, isBroadcast);
                        buf.readerIndex(ri);
                     }
                  }
               } else {
                  final Session ses = getRelaySession(header.toParentId, header.contractId);
                  if (ses != null) {
                     ses.sendRelayedMessage(header, buf, isBroadcast);
                  }
               }
            } catch (Throwable e) {
               logger.error(e.getMessage(), e);
            } finally {
               // FIXME: This is fragile -- if we delete an entity with queued work, we need to make sure we
               // release all the buffers in the queued work items.
               buf.release();
            }
         });
         worker.kick();
      } else {
         logger.error("Could not find sender entity  {} for {}", header.fromId, header.dump());
      }
   }

   @Override
   public WebRoutes getWebRoutes() {
      return cluster.getWebRoutes();
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public void broadcastServicesMessage(Message msg) {
      servicesTopic.broadcast(msg);
   }

   public void broadcastClusterMessage(Message msg) {
      clusterTopic.broadcast(msg);
   }

   public void broadcastAdminMessage(Message msg) {
      if (adminTopic != null) {
         adminTopic.broadcast(msg);
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   private void scheduleHealthCheck() {
      if (!isShuttingDown()) {
         dispatcher.dispatch(200, TimeUnit.MILLISECONDS, () -> {
            if (dispatcher.isRunning()) {
               try {
                  healthCheck();
               } catch (Throwable e) {
                  logger.error(e.getMessage(), e);
               } finally {
                  scheduleHealthCheck();
               }
            }
         });
      }
   }

   private void healthCheck() {
      cluster.service();
      final long now = System.currentTimeMillis();
      if (now - lastStatsLog > Util.ONE_MINUTE) {
         registry.logStats(false);
         lastStatsLog = System.currentTimeMillis();

      }
      // for all services in the registry
      for (final EntityInfo e : cluster.getEntities()) {
         assert e.isService();
         if (e.entityId != getEntityId()) {
            healthCheckService(e);
         }
      }

      refreshNagiosEnabledCache();
   }

   private void healthCheckService(final EntityInfo e) {
      final long now = System.currentTimeMillis();
      final Session ses = e.getSession();

      if (e.isGone()) {
         // only the leader can change the registry status
         if (cluster.isLeader()) {
            if (ses != null && ses.isConnected()) {
               registry.clearGone(e);
            } else if (now - e.getGoneSince() > 5 * Util.ONE_MINUTE) {
               logger.info("Reaping: {}", e);
               if (!e.isTetrapod()) {
                  registry.unregister(e);
               }
            }
         }
      } else {
         // push through a dummy request to help keep dispatch pool metrics fresh

         if (ses != null && now - ses.getLastHeardFrom() > 1153) {
            final long t0 = System.currentTimeMillis();
            sendRequest(new DummyRequest(), e.entityId).handle((res) -> {
               final long tf = System.currentTimeMillis() - t0;
               if (tf > 1000) {
                  logger.warn("Round trip to dispatch {} took {} ms", e, tf);
               }
            });
         }

         // only the leader can change the registry status
         if (cluster.isLeader()) {
            if (ses == null || (ses != null && !ses.isConnected())) {
               logger.info("Setting {} as GONE (healthCheckService context)", e);
               registry.setGone(e);
            }
         }
      }

      // if we don't have a connection to the service, try to spawn one
      if (serviceConnector != null && (ses == null || !ses.isConnected())) {
         DirectServiceInfo info = serviceConnector.getDirectServiceInfo(e.entityId);
         if (info.getSession() != null && info.getSession().isConnected()) {
            e.setSession(info.getSession());
         } else {
            info.considerConnecting();
         }
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   public void subscribeToCluster(Session ses, int toEntityId, int toChildId) {
      if (ses.getTheirEntityType() == Core.TYPE_SERVICE) {
         // also auto-subscribe to services topic
         logger.info("Subscribing {} to services topic-{}", toEntityId, servicesTopic.topicId);
         synchronized (servicesTopic) {
            servicesTopic.subscribe(toEntityId, toChildId, false);
            for (EntityInfo e : registry.getServices()) {
               e.queue(() -> ses.sendMessage(new ServiceAddedMessage(e), toEntityId, toChildId));
            }
         }
      }

      assert (clusterTopic != null);
      synchronized (cluster) {
         subscribe(clusterTopic.topicId, toEntityId, toChildId);
         cluster.sendClusterDetails(ses, toEntityId, toChildId, clusterTopic.topicId);
      }
   }

   @Override
   public void messageClusterMember(ClusterMemberMessage m, MessageContext ctx) {
      //      synchronized (cluster) {
      //         if (cluster.addMember(m.entityId, m.host, m.servicePort, m.clusterPort, null)) {
      //            broadcast(new ClusterMemberMessage(m.entityId, m.host, m.servicePort, m.clusterPort, m.uuid), clusterTopic);
      //         }
      //      }
   }

   public void subscribeToServices(Session ses, int toEntityId, int toChildId) {
      assert (servicesTopic != null);
      synchronized (servicesTopic) {
         subscribe(servicesTopic.topicId, toEntityId, toChildId);
         // send all current services 
         for (EntityInfo e : registry.getServices()) {
            e.queue(() -> sendPrivateMessage(new ServiceAddedMessage(e), toEntityId, toChildId));
         }
      }
   }
   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public Response requestRegister(RegisterRequest r, final RequestContext ctxA) {
      final SessionRequestContext ctx = (SessionRequestContext) ctxA;
      if (getEntityId() == 0) {
         return new Error(ERROR_SERVICE_UNAVAILABLE);
      }

      if (ctx.header.fromType == Core.TYPE_SERVICE && !cluster.isLeader()) {
         //return new Error(TetrapodContract.ERROR_NOT_LEADER);
      }

      EntityInfo info = null;
      final EntityToken t = EntityToken.decode(r.token);
      if (t != null) {
         info = registry.getEntity(t.entityId);
         if (info != null) {
            if (info.reclaimToken != t.nonce || info.parentId != getEntityId()) {
               info = null; // return error instead?
            }
         }
      }
      if (info == null) {
         info = new EntityInfo();
         info.version = ctx.header.version;
         info.host = r.host;
         info.name = r.name;
         info.reclaimToken = random.nextLong();
         info.contractId = r.contractId;
      }

      info.build = r.build;
      info.status = r.status &= ~Core.STATUS_GONE;
      info.parentId = getEntityId();
      info.type = ctx.session.getTheirEntityType();
      if (info.type == Core.TYPE_ANONYMOUS) {
         info.type = Core.TYPE_CLIENT;
         // clobber their reported host with their IP
         info.host = ctx.session.getPeerHostname();
      }

      if (info.entityId == 0) {
         info.entityId = registry.issueId();
      }

      // update & store session
      ctx.session.setTheirEntityId(info.entityId);
      ctx.session.setTheirEntityType(info.type);
      info.setSession(ctx.session);
      // deliver them their entityId immediately to avoid some race conditions with the response
      ctx.session.sendMessage(new EntityMessage(info.entityId), Core.UNADDRESSED, 0);

      if (info.type == Core.TYPE_TETRAPOD) {
         info.parentId = info.entityId;
         if (cluster.getEntity(info.entityId) != null) {
            cluster.executeCommand(new ModEntityCommand(info.entityId, info.status, 0xFFFFFFFF, info.build, info.version), null);
            return new RegisterResponse(info.entityId, getEntityId(), EntityToken.encode(info.entityId, info.reclaimToken));
         }
      }

      // for a client, we don't use raft to sync them, as they are a locally issued, non-replicated client
      if (info.type == Core.TYPE_CLIENT) {
         //return new Error(ERROR_UNSUPPORTED);
         registry.onAddEntityCommand(info);
         return new RegisterResponse(info.entityId, getEntityId(), EntityToken.encode(info.entityId, info.reclaimToken));
      }

      final int entityId = info.entityId;

      if (serviceConnector != null) {
         // set this session in the serviceConnector
         serviceConnector.seedService(info, ctx.session);
      }
      
      logger.info("Registering: {} type={}", info, info.type);
      // execute a raft registration command
      final AsyncResponder responder = new AsyncResponder(ctx);
      cluster.executeCommand(new AddEntityCommand(info), entry -> {
         if (entry != null) {
            TaskContext taskCtx = TaskContext.pushNew();
            ContextIdGenerator.setContextId(ctx.header.contextId);
            try {

               logger.info("Waited for local entityId-{} : {} : {}", entityId, entry, cluster.getCommitIndex());
               // get the real entity object after we've processed the command
               final EntityInfo entity = cluster.getEntity(entityId);

               entity.setSession(ctx.session);

               // deliver them their entityId immediately to avoid some race conditions with the response
               ctx.session.sendMessage(new EntityMessage(entity.entityId), Core.UNADDRESSED, 0);

               // avoid deadlock on raft state
               if (entity.isService() && entity.entityId != getEntityId()) {
                  entity.queue(() -> subscribeToCluster(ctx.session, entity.entityId, 0));
               }
               responder.respondWith(
                     new RegisterResponse(entity.entityId, getEntityId(), EntityToken.encode(entity.entityId, entity.reclaimToken)));
            } finally {
               taskCtx.pop();
            }

         } else {
            responder.respondWith(Response.error(ERROR_UNKNOWN));
         }
      }, true);
      return Response.PENDING;
   }

   @Override
   public Response requestUnregister(UnregisterRequest r, RequestContext ctx) {
      final EntityInfo info = registry.getEntity(ctx.header.fromParentId);
      if (info == null) {
         return new Error(ERROR_INVALID_ENTITY);
      }
      registry.unregister(info);
      return Response.SUCCESS;
   }

   @Override
   public Response requestServicesSubscribe(ServicesSubscribeRequest r, RequestContext ctxA) {
      SessionRequestContext ctx = (SessionRequestContext) ctxA;
      if (servicesTopic == null) {
         return new Error(ERROR_UNKNOWN);
      }
      subscribeToServices(ctx.session, ctx.header.fromParentId, ctx.header.fromChildId);
      return Response.SUCCESS;
   }

   @Override
   public Response requestServicesUnsubscribe(ServicesUnsubscribeRequest r, RequestContext ctx) {
      // TODO: validate
      synchronized (servicesTopic) {
         unsubscribe(servicesTopic.topicId, ctx.header.fromParentId, 0, false);
      }
      return Response.SUCCESS;
   }

   @Override
   public Response requestServiceStatusUpdate(ServiceStatusUpdateRequest r, RequestContext ctx) {
      if (ctx.header.fromParentId != 0) {
         final EntityInfo e = registry.getEntity(ctx.header.fromParentId);
         if (e != null) {
            registry.updateStatus(e, r.status, r.mask);
         } else {
            return new Error(ERROR_INVALID_ENTITY);
         }
      }
      return Response.SUCCESS;
   }

   @Override
   public Response requestAddServiceInformation(AddServiceInformationRequest req, RequestContext ctx) {
      cluster.registerContract(req.info);
      return Response.SUCCESS;
   }

   @Override
   protected void registerServiceInformation(Contract contract) {
      // do nothing, our protocol is known by all tetrapods
   }

   @Override
   public Response requestLogRegistryStats(LogRegistryStatsRequest r, RequestContext ctx) {
      registry.logStats(true);
      //   cluster.logRegistry();
      return Response.SUCCESS;
   }

   @Override
   public Response requestStorageGet(StorageGetRequest r, RequestContext ctx) {
      return new StorageGetResponse(cluster.get(r.key));
   }

   @Override
   public Response requestStorageSet(StorageSetRequest r, RequestContext ctx) {
      cluster.put(r.key, r.value);
      return Response.SUCCESS;
   }

   @Override
   public Response requestStorageDelete(StorageDeleteRequest r, RequestContext ctx) {
      cluster.delete(r.key);
      return Response.SUCCESS;
   }

   @Override
   public Response requestAdminAuthorize(AdminAuthorizeRequest r, RequestContext ctxA) {
      return adminAccounts.requestAdminAuthorize(r, ctxA);
   }

   @Override
   public Response requestAdminLogin(AdminLoginRequest r, RequestContext ctxA) {
      return adminAccounts.requestAdminLogin(r, ctxA);
   }

   @Override
   public Response requestAdminSessionToken(AdminSessionTokenRequest r, RequestContext ctx) {
      return adminAccounts.requestAdminSessionToken(r, ctx);
   }

   @Override
   public Response requestAdminChangePassword(final AdminChangePasswordRequest r, RequestContext ctxA) {
      return adminAccounts.requestAdminChangePassword(r, ctxA);
   }

   @Override
   public Response requestAdminResetPassword(AdminResetPasswordRequest r, RequestContext ctx) {
      return adminAccounts.requestAdminResetPassword(r, ctx);
   }

   @Override
   public Response requestAdminChangeRights(AdminChangeRightsRequest r, RequestContext ctx) {
      return adminAccounts.requestAdminChangeRights(r, ctx);
   }

   @Override
   public Response requestAdminCreate(AdminCreateRequest r, RequestContext ctx) {
      return adminAccounts.requestAdminCreate(r, ctx);
   }

   @Override
   public Response requestAdminDelete(AdminDeleteRequest r, RequestContext ctx) {
      return adminAccounts.requestAdminDelete(r, ctx);
   }

   //------------ building

   @Override
   public Response requestGetServiceBuildInfo(GetServiceBuildInfoRequest r, RequestContext ctx) {
      return new GetServiceBuildInfoResponse(Builder.getServiceInfo());
   }

   @Override
   public Response requestExecuteBuildCommand(ExecuteBuildCommandRequest r, RequestContext ctx) {
      Admin a = adminAccounts.getAdmin(ctx, r.authToken, Admin.RIGHTS_CLUSTER_WRITE);
      for (BuildCommand command : r.commands) {
         auditLogger.info("Admin {} [{}] executed {}", a.email, a.accountId, command.name);
         boolean success = Builder.executeCommand(command, this);
         if (!success)
            return Response.error(ERROR_UNKNOWN);
      }
      return Response.SUCCESS;
   }

   /////////////// RAFT ///////////////

   @Override
   public Response requestClusterJoin(ClusterJoinRequest r, RequestContext ctxA) {
      SessionRequestContext ctx = (SessionRequestContext) ctxA;
      if (ctx.session.getTheirEntityType() != Core.TYPE_TETRAPOD) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      return cluster.requestClusterJoin(r, clusterTopic, ctx);
   }

   @Override
   public Response requestAppendEntries(AppendEntriesRequest r, RequestContext ctx) {
      return cluster.requestAppendEntries(r, ctx);
   }

   @Override
   public Response requestVote(VoteRequest r, RequestContext ctx) {
      return cluster.requestVote(r, ctx);
   }

   @Override
   public Response requestInstallSnapshot(InstallSnapshotRequest r, RequestContext ctx) {
      return cluster.requestInstallSnapshot(r, ctx);
   }

   @Override
   public Response requestIssueCommand(IssueCommandRequest r, RequestContext ctx) {
      return cluster.requestIssueCommand(r, ctx);
   }

   @Override
   public Response requestRaftStats(RaftStatsRequest r, RequestContext ctx) {
      return cluster.requestRaftStats(r, ctx);
   }

   @Override
   public Response requestDelClusterProperty(DelClusterPropertyRequest r, RequestContext ctx) {
      Admin a = adminAccounts.getAdmin(ctx, r.authToken, Admin.RIGHTS_CLUSTER_WRITE);
      auditLogger.info("Admin {} [{}] deleted cluster property: {}.", a.email, a.accountId, r.key);
      cluster.delClusterProperty(r.key);
      return Response.SUCCESS;
   }

   @Override
   public Response requestInternalSetClusterProperty(InternalSetClusterPropertyRequest r, RequestContext ctx) {
      cluster.setClusterProperty(r.property);
      return Response.SUCCESS;
   }

   @Override
   public Response requestSetClusterProperty(SetClusterPropertyRequest r, RequestContext ctx) {
      Admin a = adminAccounts.getAdmin(ctx, r.authToken, Admin.RIGHTS_CLUSTER_WRITE);
      auditLogger.info("Admin {} [{}] created or modified cluster property: {}.", a.email, a.accountId, r.property.key);
      cluster.setClusterProperty(r.property);
      return Response.SUCCESS;
   }

   @Override
   public Response requestAdminSubscribe(AdminSubscribeRequest r, RequestContext ctx) {
      assert (adminTopic != null);
      Session ses = ((SessionRequestContext) ctx).session;
      logger.info("Subscribing admin({}) {} {}", adminTopic.topicId, ctx.header.fromParentId, ctx.header.fromChildId);
      synchronized (cluster) {
         subscribe(adminTopic.topicId, ctx.header.fromParentId, ctx.header.fromChildId);
      }
      logger.info("Subscribing services({}) {} {}", servicesTopic.topicId, ctx.header.fromParentId, ctx.header.fromChildId);
      subscribeToServices(ses, ctx.header.fromParentId, ctx.header.fromChildId);
      return new AdminSubscribeResponse(entityId, adminTopic.topicId);
   }

   @Override
   public Response requestSetWebRoot(SetWebRootRequest r, RequestContext ctx) {
      Admin a = adminAccounts.getAdmin(ctx, r.authToken, Admin.RIGHTS_CLUSTER_WRITE);
      if (r.def != null) {
         auditLogger.info("Admin {} [{}] updated webroot.  New webroot: {}, {}, {}", a.email, a.accountId, r.def.name, r.def.path,
               r.def.file);
         cluster.setWebRoot(r.def);
         return Response.SUCCESS;
      }
      return new Error(ERROR_INVALID_DATA);
   }

   @Override
   public Response requestDelWebRoot(DelWebRootRequest r, RequestContext ctx) {
      Admin a = adminAccounts.getAdmin(ctx, r.authToken, Admin.RIGHTS_CLUSTER_WRITE);
      if (r.name != null) {
         auditLogger.info("Admin {} [{}] deleted the webroot at {}.", a.email, a.accountId, r.name);
         cluster.delWebRoot(r.name);
         return Response.SUCCESS;
      }
      return new Error(ERROR_INVALID_DATA);
   }

   @Override
   public Response requestLock(LockRequest r, RequestContext ctx) {
      final DistributedLock lock = cluster.getLock(r.key);
      if (lock.lock(r.leaseMillis, r.waitMillis)) {
         return new LockResponse(lock.uuid);
      }
      return Response.error(ERROR_TIMEOUT);
   }

   @Override
   public Response requestUnlock(UnlockRequest r, RequestContext ctx) {
      final DistributedLock lock = new DistributedLock(r.key, r.uuid, cluster);
      lock.unlock();
      return Response.SUCCESS;
   }

   @Override
   public Response requestSnapshot(SnapshotRequest r, RequestContext ctx) {
      cluster.snapshot();
      return Response.SUCCESS;
   }

   @Override
   public Response requestClaimOwnership(ClaimOwnershipRequest r, RequestContext ctx) {
      if (ctx.header.fromType != TYPE_SERVICE)
         return new Error(ERROR_INVALID_RIGHTS);

      return cluster.requestClaimOwnership(r, (SessionRequestContext) ctx);
   }

   @Override
   public Response requestRetainOwnership(RetainOwnershipRequest r, RequestContext ctx) {
      if (ctx.header.fromType != TYPE_SERVICE)
         return new Error(ERROR_INVALID_RIGHTS);

      return cluster.requestRetainOwnership(r, (SessionRequestContext) ctx);
   }

   @Override
   public Response requestReleaseOwnership(ReleaseOwnershipRequest r, RequestContext ctx) {
      if (ctx.header.fromType != TYPE_SERVICE)
         return new Error(ERROR_INVALID_RIGHTS);

      return cluster.requestReleaseOwnership(r, (SessionRequestContext) ctx);
   }

   @Override
   public Response requestSubscribeOwnership(SubscribeOwnershipRequest r, RequestContext ctx) {
      if (ctx.header.fromType != TYPE_SERVICE)
         return new Error(ERROR_INVALID_RIGHTS);

      return cluster.requestSubscribeOwnership(r, (SessionRequestContext) ctx);
   }

   @Override
   public Response requestUnsubscribeOwnership(UnsubscribeOwnershipRequest r, RequestContext ctx) {
      if (ctx.header.fromType != TYPE_SERVICE)
         return new Error(ERROR_INVALID_RIGHTS);

      return cluster.requestUnsubscribeOwnership(r, ctx);
   }

   @Override
   public Response requestRaftLeader(RaftLeaderRequest r, RequestContext ctx) {
      ServerAddress leader = cluster.getLeader();
      if (leader != null) {
         return new RaftLeaderResponse(leader);
      }
      return Response.error(TetrapodContract.ERROR_NOT_LEADER);
   }

   public void shutdownServices() {
      for (EntityInfo e : registry.getServices()) {
         if (e.entityId != getEntityId() && e.host.equals(Util.getHostName())) {
            sendRequest(new InternalShutdownRequest(), e.entityId).log();
         }
      }
   }

   private boolean setNagiosAlertsEnabled(String host, String nagiosDomain, String nagiosUser, String nagiosPwd, boolean enable)
         throws IOException {
      final String url = String.format("http://%s/nagios/cgi-bin/cmd.cgi?cmd_typ=%d&cmd_mod=2&ahas=true&host=%s&btnSubmit=Commit",
            nagiosDomain, enable ? 28 : 29, host);
      String res = Util.httpGet(url, nagiosUser, nagiosPwd);
      //logger.info("{} =>\n{}", url, res);
      return res != null && res.contains("Your command request was successfully submitted to Nagios for processing");
   }

   private boolean getNagiosAlertsEnabled(String host, String nagiosDomain, String nagiosUser, String nagiosPwd) throws IOException {
      final String url = String.format("http://%s/nagios/cgi-bin/statusjson.cgi?query=host&hostname=%s", nagiosDomain, host);
      String res = Util.httpGet(url, nagiosUser, nagiosPwd);
      // logger.info("{} =>\n{}", url, res);
      JSONObject jo = new JSONObject(res);
      return jo.getJSONObject("data").getJSONObject("host").getBoolean("notifications_enabled");
   }

   private final Map<String, Boolean> nagiosEnabled  = new ConcurrentHashMap<>();
   private long                       lastNagiosPoll = 0;

   private void refreshNagiosEnabledCache() {
      synchronized (nagiosEnabled) {
         if (System.currentTimeMillis() - lastNagiosPoll < Util.ONE_SECOND * 15) {
            return;
         }
         lastNagiosPoll = System.currentTimeMillis();
      }
      final String user = Util.getProperty("nagios.user");
      final String pwd = Util.getProperty("nagios.password");
      final String domain = Util.getProperty("nagios.host");
      if (domain != null && user != null && pwd != null) {
         try {
            for (String host : nagiosEnabled.keySet()) {
               Boolean old = nagiosEnabled.get(host);
               Boolean val = getNagiosAlertsEnabled(host, domain, user, pwd);
               nagiosEnabled.put(host, val);
               if (val != null && !val.equals(old)) {
                  broadcastAdminMessage(new NagiosStatusMessage(host, val));
               }
            }
         } catch (Exception e) {
            logger.error(e.getMessage(), e);
         }
      }
   }

   @Override
   public Response requestNagiosStatus(NagiosStatusRequest r, RequestContext ctx) {
      final String user = Util.getProperty("nagios.user");
      final String pwd = Util.getProperty("nagios.password");
      final String domain = Util.getProperty("nagios.host");
      Admin a = adminAccounts.getAdmin(ctx, r.authToken, Admin.RIGHTS_CLUSTER_WRITE);
      if (domain == null || user == null || pwd == null) {
         return Response.error(ERROR_NOT_CONFIGURED);
      }
      try {
         Boolean enabled = nagiosEnabled.get(r.hostname);
         if (enabled == null) {
            enabled = getNagiosAlertsEnabled(r.hostname, domain, user, pwd);
         }
         if (r.toggle) {
            if (setNagiosAlertsEnabled(r.hostname, domain, user, pwd, !enabled)) {
               auditLogger.info("Admin {} [{}] {} nagios.", a.email, a.accountId, enabled ? "enabled" : "disabled");
               enabled = !enabled;
            }
         }
         nagiosEnabled.put(r.hostname, enabled);

         return new NagiosStatusResponse(enabled);
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
         return Response.error(ERROR_UNKNOWN);
      }
   }

}
