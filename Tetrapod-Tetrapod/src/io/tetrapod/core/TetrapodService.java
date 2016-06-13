package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.*;
import static io.tetrapod.protocol.core.CoreContract.*;
import static io.tetrapod.protocol.core.TetrapodContract.ERROR_UNKNOWN_ENTITY_ID;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.ServiceConnector.DirectServiceInfo;
import io.tetrapod.core.Session.RelayHandler;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.core.pubsub.Topic;
import io.tetrapod.core.registry.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;
import io.tetrapod.core.storage.*;
import io.tetrapod.core.utils.*;
import io.tetrapod.core.web.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.raft.*;
import io.tetrapod.protocol.storage.*;

/**
 * The tetrapod service is the core cluster service which handles message routing, cluster
 * management, service discovery, and load balancing of client connections
 */
public class TetrapodService extends DefaultService
      implements TetrapodContract.API, StorageContract.API, RaftContract.API, RelayHandler, EntityRegistry.RegistryBroadcaster {

   public static final Logger        logger                = LoggerFactory.getLogger(TetrapodService.class);
   public static final Logger        auditLogger           = LoggerFactory.getLogger("audit");

   public final SecureRandom         random                = new SecureRandom();

   private Topic                     clusterTopic;
   private Topic                     servicesTopic;
   private Topic                     adminTopic;

   private final TetrapodWorker      worker;

   protected final TetrapodCluster   cluster               = new TetrapodCluster(this);

   public final EntityRegistry       registry              = new EntityRegistry(this, cluster);

   private AdminAccounts             adminAccounts;

   private final List<Server>        servers               = new ArrayList<>();
   private final List<Server>        httpServers           = new ArrayList<>();

   private long                      lastStatsLog;

   private final LinkedList<Integer> clientSessionsCounter = new LinkedList<>();

   public TetrapodService() throws IOException {
      super(new TetrapodContract());

      worker = new TetrapodWorker(this);
      addContracts(new StorageContract());
      addContracts(new RaftContract());

      // add tetrapod web routes
      for (WebRoute r : contract.getWebRoutes()) {
         getWebRoutes().setRoute(r.path, r.contractId, r.structId);
      }
      // add the tetrapod admin web root
      cluster.getWebRootDirs().put("tetrapod", new WebRootLocalFilesystem("/", new File("webContent")));

      addSubscriptionHandler(new TetrapodContract.Registry(), registry);
   }

   @Override
   public void startNetwork(ServerAddress address, String token, Map<String, String> otherOpts) throws Exception {
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

         clusterTopic = publishTopic();
         servicesTopic = publishTopic();

         adminTopic = publishTopic();
         adminTopic.addListener((toEntityId, resub) -> {
            synchronized (cluster) {
               cluster.sendAdminDetails(findSession(toEntityId), toEntityId, adminTopic.topicId);
               for (String host : nagiosEnabled.keySet()) {
                  broadcastAdminMessage(new NagiosStatusMessage(host, nagiosEnabled.get(host)));
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
    * We need to override the connectToCluster in superclass because that one tries to
    * reconnect to other tetrapods, but the clusterClient connection is a special loopback
    * connection in the tetrapod, so we should only ever reconnect back to ourselves.
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
      return new ServiceCommand[] {
            new ServiceCommand("Log Registry Stats", null, LogRegistryStatsRequest.CONTRACT_ID, LogRegistryStatsRequest.STRUCT_ID, false),
            new ServiceCommand("Close Client Connection", null, CloseClientConnectionRequest.CONTRACT_ID,
                  CloseClientConnectionRequest.STRUCT_ID, true), };
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
         ses = new WebHttpSession(ch, pod, contentRootMap, webSockets);
         ses.setRelayHandler(pod);
         ses.setMyEntityId(getEntityId());
         ses.setMyEntityType(Core.TYPE_TETRAPOD);
         ses.setTheirEntityType(Core.TYPE_CLIENT);
         ses.addSessionListener(new Session.Listener() {
            @Override
            public void onSessionStop(Session ses) {
               onEntityDisconnected(ses);
            }

            @Override
            public void onSessionStart(Session ses) {}
         });
         return ses;
      }
   }

   public void onEntityDisconnected(Session ses) {
      if (ses instanceof WebHttpSession) {
         logger.debug("Session Stopped: {}", ses);
      } else {
         logger.info("Session Stopped: {}", ses);
      }
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
    * As a Tetrapod service, we can't start serving as one until we've registered & fully
    * sync'ed with the cluster, or self-registered if we are the first one. We call this
    * once this criteria has been reached
    */
   @Override
   public void onReadyToServe() {
      logger.info(" ***** READY TO SERVE ***** ");
      if (isStartingUp()) {
         try {
            AdminAuthToken.setSecret(getSharedSecret());
            adminAccounts = new AdminAccounts(cluster);

            // FIXME:
            // Ensure we have all of the needed WebRootDir files installed before we open http ports

            // create servers
            Server httpServer = (new Server(getHTTPPort(), new WebSessionFactory(cluster.getWebRootDirs(), "/sockets"), dispatcher));
            servers.add((httpServer));
            httpServers.add(httpServer);

            // create secure port servers, if configured
            if (sslContext != null) {
               httpServer = new Server(getHTTPSPort(), new WebSessionFactory(cluster.getWebRootDirs(), "/sockets"), dispatcher, sslContext,
                     false);
               servers.add((httpServer));
               httpServers.add(httpServer);
            }
            servers.add(new Server(getPublicPort(), new TypedSessionFactory(Core.TYPE_ANONYMOUS), dispatcher, sslContext, false));
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

   // Pause will close the HTTP and HTTPS ports on the tetrapod service
   @Override
   public void onPaused() {
      for (Server httpServer : httpServers) {
         httpServer.close();
      }
   }

   // Purge will boot all non-admin sessions from the tetrapod service
   @Override
   public void onPurged() {
      for (Server httpServer : httpServers) {
         httpServer.purge();
      }
   }

   // UnPause will restart the HTTP listeners on the tetrapod service.
   @Override
   public void onUnpaused() {
      for (Server httpServer : httpServers) {
         try {
            httpServer.start().sync();
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

   /**
    * Validates a long-polling session to an entityId
    */
   @Override
   public boolean validate(int entityId, long token) {
      final EntityInfo e = registry.getEntity(entityId);
      if (e != null) {
         if (e.reclaimToken == token) {
            // HACK: as a side-effect, we update last contact time
            e.setLastContact(System.currentTimeMillis());
            if (e.isGone()) {
               registry.updateStatus(e, 0, Core.STATUS_GONE);
            }
            return true;
         }
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
               if (header.topicId != 0) {
                  if (isBroadcast) {
                     broadcastTopic(sender, header, buf);
                  }
               } else if ((header.flags & MessageHeader.FLAGS_ALTERNATE) != 0) {
                  if (isBroadcast) {
                     broadcastAlt(sender, header, buf);
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
         });
         worker.kick();
      } else {
         logger.error("Could not find sender entity  {} for {}", header.fromId, header.dump());
      }
   }

   private void broadcastTopic(final EntityInfo publisher, final MessageHeader header, final ByteBuf buf) throws IOException {
      if (header.toId == UNADDRESSED || header.toId == getEntityId()) {
         final RegistryTopic topic = publisher.getTopic(header.topicId);
         if (topic != null) {
            synchronized (topic) {
               for (final Subscriber s : topic.getSubscribers()) {
                  broadcastTopic(publisher, s, topic, header, buf);
               }
            }
         } else {
            logger.warn("Could not find topic {} for entity {} : {}", header.topicId, publisher, header.dump());
            sendMessage(new TopicNotFoundMessage(publisher.entityId, header.topicId), publisher.entityId);
         }
      } else {
         // relay to destination 
         final Session ses = getRelaySession(header.toId, header.contractId);
         if (ses != null) {
            ses.sendRelayedMessage(header, buf, true);
         }
      }
   }

   private void broadcastAlt(final EntityInfo publisher, final MessageHeader header, final ByteBuf buf) throws IOException {
      final int myId = getEntityId();
      final boolean myChildOriginated = publisher.parentId == myId;
      final boolean toAll = header.toId == UNADDRESSED;
      for (EntityInfo e : registry.getEntities()) {
         if (e.isTetrapod() && e.entityId != myId) {
            if (myChildOriginated)
               broadcastToAlt(e, header, buf);
            continue;
         }
         if (e.isService()) {
            continue;
         }
         if (toAll || e.getAlternateId() == header.toId) {
            broadcastToAlt(e, header, buf);
         }
      }
   }

   private void broadcastTopic(final EntityInfo publisher, final Subscriber sub, final RegistryTopic topic, final MessageHeader header,
         final ByteBuf buf) throws IOException {
      final int ri = buf.readerIndex();
      final EntityInfo e = registry.getEntity(sub.entityId);
      if (e != null) {
         if (e.entityId == getEntityId()) {
            // dispatch to self
            ByteBufDataSource reader = new ByteBufDataSource(buf);
            final Object obj = StructureFactory.make(header.contractId, header.structId);
            final Message msg = (obj instanceof Message) ? (Message) obj : null;
            if (msg != null) {
               msg.read(reader);
               clusterClient.getSession().dispatchMessage(header, msg);
            } else {
               logger.warn("Could not read message for self-dispatch {}", header.dump());
            }
            buf.readerIndex(ri);
         } else {
            if (e.parentId == getEntityId() || e.isTetrapod()) {
               final Session session = findSession(e);
               if (session != null) {
                  // rebroadcast this message if it was published by one of our children and we're sending it to another tetrapod
                  final boolean keepBroadcasting = e.isTetrapod() && publisher.parentId == getEntityId();
                  session.sendRelayedMessage(header, buf, keepBroadcasting);
                  buf.readerIndex(ri);
               } else {
                  if (!e.isGone()) {
                     logger.error("Could not find session for {} {}", e, header.dump());
                  }
               }
            }
         }
      } else {
         logger.error("Could not find subscriber {} for topic {}", sub.entityId, topic);
      }
   }

   private void broadcastToAlt(final EntityInfo e, final MessageHeader header, final ByteBuf buf) throws IOException {
      final int ri = buf.readerIndex();
      if (!e.isGone()) {
         final Session session = findSession(e);
         if (session != null) {
            final boolean keepBroadcasting = e.isTetrapod();
            session.sendRelayedMessage(header, buf, keepBroadcasting);
            buf.readerIndex(ri);
         } else {
            logger.error("Could not find session for {} {}", e, header.dump());
         }
      }
   }

   @Override
   public WebRoutes getWebRoutes() {
      return cluster.getWebRoutes();
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public void broadcastServicesMessage(Message msg) {
      if (servicesTopic != null) {
         servicesTopic.broadcast(msg);
      }
   }

   public void broadcastClusterMessage(Message msg) {
      if (clusterTopic != null) {
         clusterTopic.broadcast(msg);
      }
   }

   public void broadcast(Message msg, RegistryTopic topic) {
      if (topic != null) {
         synchronized (topic) {
            // OPTIMIZE: call broadcast() directly instead of through loop-back
            Session ses = clusterClient.getSession();
            if (ses != null) {
               ses.sendTopicBroadcastMessage(msg, 0, topic.topicId);
            } else {
               logger.error("broadcast failed: no session for loopback connection");
            }
         }
      }
   }

   public void broadcastAdminMessage(Message msg) {
      if (adminTopic != null) {
         adminTopic.broadcast(msg);
      }
   }

   @Override
   public boolean sendBroadcastMessage(Message msg, int toEntityId, int topicId) {
      final EntityInfo e = registry.getEntity(toEntityId);
      if (e != null) {
         if (e.isService()) {
            return super.sendBroadcastMessage(msg, toEntityId, topicId);
         } else {
            final Session ses = findSession(e);
            if (ses != null && ses.isConnected()) {
               ses.sendMessage(msg, toEntityId);
               return true;
            }
         }
      }
      return false;
   }

   @Override
   public boolean sendPrivateMessage(Message msg, int toEntityId, int topicId) {
      final EntityInfo e = registry.getEntity(toEntityId);
      if (e != null) {
         if (e.isService()) {
            return super.sendPrivateMessage(msg, toEntityId, topicId);
         } else if (e.hasConnectedSession()) {
            e.getSession().sendMessage(msg, toEntityId);
            return true;
         }
      }
      return false;
   }

   @Override
   public void sendMessage(Message msg, int toEntityId) {
      final EntityInfo e = registry.getEntity(toEntityId);
      if (e != null) {
         if (e.isService()) {
            super.sendMessage(msg, toEntityId);
         } else if (e.hasConnectedSession()) {
            e.getSession().sendMessage(msg, toEntityId);
         }
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   private void scheduleHealthCheck() {
      if (!isShuttingDown()) {
         dispatcher.dispatch(1, TimeUnit.SECONDS, () -> {
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

         final int clients = registry.getNumActiveClients();
         synchronized (clientSessionsCounter) {
            clientSessionsCounter.addLast(clients);
            if (clientSessionsCounter.size() > 1440) {
               clientSessionsCounter.removeFirst();
            }
         }
      }

      // for all of our clients:
      for (final EntityInfo e : registry.getChildren()) {
         if (!e.isService()) {
            healthCheckClient(e);
         }
      }

      // for all services in the registry
      for (final EntityInfo e : cluster.getEntities()) {
         if (e.isService() && e.entityId != getEntityId()) {
            healthCheckService(e);
         }
      }

      refreshNagiosEnabledCache();
   }

   private void healthCheckClient(final EntityInfo e) {
      final long now = System.currentTimeMillis();
      if (e.isGone()) {
         if (now - e.getGoneSince() > Util.ONE_MINUTE) {
            logger.info("Reaping: {}", e);
            registry.unregister(e);
         }
      } else {
         // special check for long-polling clients
         if (e.getLastContact() != null) {
            if (now - e.getLastContact() > Util.ONE_MINUTE) {
               e.setLastContact(null);
               registry.setGone(e);
            }
         }
      }
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

   public void subscribeToCluster(Session ses, int toEntityId) {
      if (ses.getTheirEntityType() == Core.TYPE_SERVICE) {
         // also auto-subscribe to services topic
         synchronized (servicesTopic) {
            subscribe(servicesTopic.topicId, toEntityId);
            for (EntityInfo e : registry.getServices()) {
               e.queue(() -> ses.sendMessage(new ServiceAddedMessage(e), toEntityId));
            }
         }
      }

      assert (clusterTopic != null);
      synchronized (cluster) {
         subscribe(clusterTopic.topicId, toEntityId);
         cluster.sendClusterDetails(ses, toEntityId, clusterTopic.topicId);
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

   public void subscribeToAdmin(Session ses, int toEntityId) {
      assert (adminTopic != null);
      synchronized (cluster) {
         subscribe(adminTopic.topicId, toEntityId);
         //cluster.sendAdminDetails(ses, toEntityId, adminTopic.topicId);
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public Response requestKeepAlive(KeepAliveRequest r, RequestContext ctx) {
      return Response.SUCCESS;
   }

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
      ctx.session.sendMessage(new EntityMessage(info.entityId), Core.UNADDRESSED);

      if (info.type == Core.TYPE_TETRAPOD) {
         info.parentId = info.entityId;
         if (cluster.getEntity(info.entityId) != null) {
            cluster.executeCommand(new ModEntityCommand(info.entityId, info.status, 0xFFFFFFFF, info.build, info.version), null);
            return new RegisterResponse(info.entityId, getEntityId(), EntityToken.encode(info.entityId, info.reclaimToken));
         }
      }

      // for a client, we don't use raft to sync them, as they are a locally issued, non-replicated client
      if (info.type == Core.TYPE_CLIENT) {
         // deliver them their entityId immediately to avoid some race conditions with the response
         ctx.session.sendMessage(new EntityMessage(info.entityId), Core.UNADDRESSED);
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
            logger.info("Waited for local entityId-{} : {} : {}", entityId, entry, cluster.getCommitIndex());
            // get the real entity object after we've processed the command
            final EntityInfo entity = cluster.getEntity(entityId);

            entity.setSession(ctx.session);

            // deliver them their entityId immediately to avoid some race conditions with the response
            ctx.session.sendMessage(new EntityMessage(entity.entityId), Core.UNADDRESSED);

            // avoid deadlock on raft state
            if (entity.isService() && entity.entityId != getEntityId()) {
               dispatcher.dispatch(() -> subscribeToCluster(ctx.session, entity.entityId));
            }
            responder.respondWith(
                  new RegisterResponse(entity.entityId, getEntityId(), EntityToken.encode(entity.entityId, entity.reclaimToken)));

         } else {
            responder.respondWith(Response.error(ERROR_UNKNOWN));
         }
      }, true);
      return Response.PENDING;
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
   public Response requestServicesSubscribe(ServicesSubscribeRequest r, RequestContext ctxA) {
      SessionRequestContext ctx = (SessionRequestContext) ctxA;
      if (servicesTopic == null) {
         return new Error(ERROR_UNKNOWN);
      }

      if (!ctx.isFromService()) {
         if (adminAccounts == null) {
            return new Error(ERROR_SERVICE_UNAVAILABLE);
         }
         if (!adminAccounts.isValidAdminRequest(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_READ)) {
            return new Error(ERROR_INVALID_RIGHTS);
         }
      }

      synchronized (servicesTopic) {
         // send all current services
         //EntityInfo sub = registry.getEntity(ctx.header.fromId);
         subscribe(servicesTopic.topicId, ctx.header.fromId);
         for (EntityInfo e : registry.getServices()) {
            e.queue(() -> ctx.session.sendMessage(new ServiceAddedMessage(e), ctx.header.fromId));
         }
         // send all current services
         //         for (EntityInfo e : registry.getServices()) {
         //            e.queue(() -> sendPrivateMessage(new ServiceAddedMessage(e), ctx.header.fromId, servicesTopic.topicId));
         //         }
      }
      return Response.SUCCESS;
   }

   @Override
   public Response requestServicesUnsubscribe(ServicesUnsubscribeRequest r, RequestContext ctx) {
      // TODO: validate
      synchronized (servicesTopic) {
         unsubscribe(servicesTopic.topicId, ctx.header.fromId);
      }
      return Response.SUCCESS;
   }

   @Override
   public Response requestServiceStatusUpdate(ServiceStatusUpdateRequest r, RequestContext ctx) {
      if (ctx.header.fromId != 0) {
         final EntityInfo e = registry.getEntity(ctx.header.fromId);
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

   @Override
   public Response requestSetAlternateId(SetAlternateIdRequest r, RequestContext ctx) {
      EntityInfo e = registry.getEntity(r.entityId);
      if (e != null) {
         e.setAlternateId(r.alternateId);
         return Response.SUCCESS;
      }
      return Response.error(ERROR_INVALID_ENTITY);
   }

   @Override
   public Response requestVerifyEntityToken(VerifyEntityTokenRequest r, RequestContext ctx) {
      EntityToken t = EntityToken.decode(r.token);
      if (t.entityId == r.entityId) {
         EntityInfo e = registry.getEntity(r.entityId);
         if (e != null) {
            synchronized (e) {
               if (e.reclaimToken == t.nonce)
                  return Response.SUCCESS;
            }
         }
      }
      return Response.error(ERROR_INVALID_TOKEN);
   }

   @Override
   public Response requestGetEntityInfo(GetEntityInfoRequest r, RequestContext ctx) {
      final EntityInfo e = registry.getEntity(r.entityId);
      if (e != null) {
         synchronized (e) {
            final Session s = e.getSession();
            if (s != null) {
               if (e.isClient() && s instanceof WebHttpSession) {
                  WebHttpSession ws = (WebHttpSession) s;
                  return new GetEntityInfoResponse(e.build, e.name, s.getPeerHostname(), ws.getHttpReferrer(), ws.getDomain());
               }
               return new GetEntityInfoResponse(e.build, e.name, s.getPeerHostname(), null, null);
            } else {
               return new GetEntityInfoResponse(e.build, e.name, null, null, null);
            }
         }
      }

      return Response.error(ERROR_UNKNOWN_ENTITY_ID);
   }

   @Override
   public Response requestSetEntityReferrer(SetEntityReferrerRequest r, RequestContext ctx) {
      final EntityInfo e = registry.getEntity(ctx.header.fromId);
      if (e != null) {
         synchronized (e) {
            final Session s = e.getSession();
            if (s != null && s instanceof WebHttpSession) {
               ((WebHttpSession) s).setHttpReferrer(r.referrer);
               return Response.SUCCESS;
            } else {
               return Response.error(ERROR_INVALID_DATA);
            }
         }
      }

      return Response.error(ERROR_UNKNOWN_ENTITY_ID);
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
      Admin a = adminAccounts.getAdmin(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_WRITE);
      if (!adminAccounts.isValidAdminRequest(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_WRITE)) {
         auditLogger.info("Admin{} [{}] failed to delete cluster property: {}.", a.email, a.accountId, r.key);
         return new Error(ERROR_INVALID_RIGHTS);
      }
      auditLogger.info("Admin {} [{}] deleted cluster property: {}.", a.email, a.accountId, r.key);
      cluster.delClusterProperty(r.key);
      return Response.SUCCESS;
   }

   @Override
   public Response requestSetClusterProperty(SetClusterPropertyRequest r, RequestContext ctx) {
      Admin a;
      if (ctx.isFromService()) {
         a = adminAccounts.getAdminInternal(r.adminToken, Admin.RIGHTS_CLUSTER_WRITE);
      } else {
         a = adminAccounts.getAdmin(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_WRITE);
      }
      if (!ctx.isFromService() && !adminAccounts.isValidAdminRequest(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_WRITE)) {
         auditLogger.info("Admin {} [{}] failed to create or modify cluster property: {}.", a.email, a.accountId, r.property.key);
         return new Error(ERROR_INVALID_RIGHTS);
      }
      auditLogger.info("Admin {} [{}] created or modified cluster property: {}.", a.email, a.accountId, r.property.key);
      cluster.setClusterProperty(r.property);
      return Response.SUCCESS;
   }

   @Override
   public Response requestAdminSubscribe(AdminSubscribeRequest r, RequestContext ctx) {
      if (!adminAccounts.isValidAdminRequest(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_READ)) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      subscribeToAdmin(((SessionRequestContext) ctx).session, ctx.header.fromId);
      return Response.SUCCESS;
   }

   @Override
   public Response requestSetWebRoot(SetWebRootRequest r, RequestContext ctx) {
      Admin a = adminAccounts.getAdmin(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_WRITE);
      if (!adminAccounts.isValidAdminRequest(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_WRITE)) {
         auditLogger.info("Admin {} [{}] failed to update webroot to {}, {}, {}",
               a.email, a.accountId, r.def.name, r.def.path, r.def.file);
         return new Error(ERROR_INVALID_RIGHTS);
      }
      if (r.def != null) {
         auditLogger.info("Admin {} [{}] updated webroot.  New webroot: {}, {}, {}",
               a.email, a.accountId, r.def.name, r.def.path, r.def.file);
         cluster.setWebRoot(r.def);
         return Response.SUCCESS;
      }
      return new Error(ERROR_INVALID_DATA);
   }

   @Override
   public Response requestDelWebRoot(DelWebRootRequest r, RequestContext ctx) {
      Admin a = adminAccounts.getAdmin(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_WRITE);
      if (!adminAccounts.isValidAdminRequest(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_WRITE)) {
         auditLogger.info("Admin {} [{}] failed to delete the webroot at {}.", a.email, a.accountId, r.name);
         return new Error(ERROR_INVALID_RIGHTS);
      }
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
      if (!adminAccounts.isValidAdminRequest(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_WRITE)) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
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
   public Response requestTetrapodClientSessions(TetrapodClientSessionsRequest r, RequestContext ctx) {
      synchronized (clientSessionsCounter) {
         return new TetrapodClientSessionsResponse(Util.toIntArray(clientSessionsCounter));
      }
   }

   @Override
   public Response requestCloseClientConnection(CloseClientConnectionRequest r, RequestContext ctx) {
      resetServiceConnector(true);
      int accountId = Integer.parseInt(r.data);
      for (EntityInfo e : registry.getEntities()) {
         if (!e.isService() && e.getAlternateId() == accountId && !e.isGone()) {
            Session session = findSession(e);
            if (session != null) {
               session.close();
               return Response.SUCCESS;
            }
         }
      }
      return Response.error(ERROR_INVALID_ENTITY);
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
            sendRequest(new ShutdownRequest(), e.entityId);
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
      Admin a = adminAccounts.getAdmin(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_WRITE);
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
