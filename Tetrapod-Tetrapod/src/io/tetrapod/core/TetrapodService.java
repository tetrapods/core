package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.*;
import static io.tetrapod.protocol.core.CoreContract.*;
import static io.tetrapod.protocol.core.TetrapodContract.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.base64.Base64Dialect;
import io.tetrapod.core.Session.RelayHandler;
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

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.Properties;
import java.util.concurrent.*;

import org.slf4j.*;

/**
 * The tetrapod service is the core cluster service which handles message routing, cluster management, service discovery, and load balancing
 * of client connections
 */
public class TetrapodService extends DefaultService implements TetrapodContract.API, StorageContract.API, RaftContract.API, RelayHandler,
      io.tetrapod.core.registry.Registry.RegistryBroadcaster {

   public static final Logger                      logger            = LoggerFactory.getLogger(TetrapodService.class);

   private static final String                     SHARED_SECRET_KEY = "tetrapod.shared.secret";

   public final SecureRandom                       random            = new SecureRandom();

   public final io.tetrapod.core.registry.Registry registry          = new io.tetrapod.core.registry.Registry(this);

   private Topic                                   clusterTopic;
   private Topic                                   registryTopic;
   private Topic                                   servicesTopic;
   private Topic                                   adminTopic;

   private final Object                            registryTopicLock = new Object();
   private final Object                            servicesTopicLock = new Object();

   private final TetrapodWorker                    worker;

   protected final TetrapodCluster                 cluster           = new TetrapodCluster(this);

   private AdminAccounts                           adminAccounts;

   private final List<Server>                      servers           = new ArrayList<Server>();
   private final List<Server>                      httpServers       = new ArrayList<Server>();

   private long                                    lastStatsLog;

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
      cluster.startListening();
      cluster.loadProperties();
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

         final EntityInfo e = new EntityInfo(entityId, 0, reclaimToken, Util.getHostName(), 0, Core.TYPE_TETRAPOD, getShortName(),
               buildNumber, 0, getContractId());
         registry.register(e);
         logger.info(String.format("SELF-REGISTERED: 0x%08X %s", entityId, e));

         clusterTopic = registry.publish(entityId);
         registryTopic = registry.publish(entityId);
         servicesTopic = registry.publish(entityId);
         adminTopic = registry.publish(entityId);

         //   cluster.startListening();
         // Establish a special loopback connection to ourselves
         // connects to self on localhost on our clusterport
         clusterClient.connect("localhost", getClusterPort(), dispatcher).sync();

      } catch (Exception ex) {
         fail(ex);
      }
   }

   @Override
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
            LogRegistryStatsRequest.STRUCT_ID, false) };
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

   private class TypedSessionFactory extends WireSessionFactory {

      private TypedSessionFactory(byte type) {
         super(TetrapodService.this, type, new Session.Listener() {
            @Override
            public void onSessionStop(Session ses) {
               logger.info("Session Stopped: {}", ses);
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
               logger.info("Session Stopped: {}", ses);
               onEntityDisconnected(ses);
            }

            @Override
            public void onSessionStart(Session ses) {}
         });
         return ses;
      }
   }

   public void onEntityDisconnected(Session ses) {
      if (ses.getTheirEntityId() != 0) {
         final EntityInfo e = registry.getEntity(ses.getTheirEntityId());
         if (e != null) {
            registry.setGone(e);
         }
      }
   }

   private void importClusterPropertiesIntoRaft() {
      if (!Util.getProperty("props.init", false)) {
         logger.warn("###### LOADING CLUSTER PROPERTIES INTO RAFT STATE MACHINE ######");

         Properties props = new Properties();
         Launcher.loadClusterProperties(props);
         String importFile = Util.getProperty("raft.import.properties");
         if (importFile != null) {
            Launcher.loadProperties(importFile, props);
         }
         for (Object key : props.keySet()) {
            cluster.setClusterProperty(new ClusterProperty(key.toString(), false, props.getProperty(key.toString())));
         }

         // secrets
         String secrets = props.getProperty("secrets");

         props = new Properties();

         if (secrets != null) {
            props.put("secrets", secrets);
            Launcher.loadSecretProperties(props);
         }

         String importSecretsFile = Util.getProperty("raft.import.secret.properties");
         if (importSecretsFile != null) {
            Launcher.loadProperties(importSecretsFile, props);
         }

         for (Object key : props.keySet()) {
            cluster.setClusterProperty(new ClusterProperty(key.toString(), true, props.getProperty(key.toString())));
         }

         // save property indicating we've imported
         cluster.setClusterProperty(new ClusterProperty("props.init", false, "true"));
         Util.sleep(1000);
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
         importClusterPropertiesIntoRaft();
         try {
            AuthToken.setSecret(getSharedSecret());
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
         // scheduleHealthCheck();
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
      if (cluster != null) {
         cluster.shutdown();
      }
   }

   /**
    * Extract a shared secret key for seeding server HMACs
    */
   public byte[] getSharedSecret() {
      String secret = Util.getProperty(SHARED_SECRET_KEY);
      if (secret == null) {
         secret = AuthToken.generateSharedSecret();
         cluster.setClusterProperty(new ClusterProperty(SHARED_SECRET_KEY, true, secret));
      }
      return AESEncryptor.decodeBase64(secret, Base64Dialect.STANDARD);
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
               registry.updateStatus(e, e.status & ~Core.STATUS_GONE);
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
                  switch (header.toType) {
                     case MessageHeader.TO_TOPIC:
                        if (isBroadcast) {
                           broadcastTopic(sender, header, buf);
                        }
                        break;

                     case MessageHeader.TO_ENTITY:
                        final Session ses = getRelaySession(header.toId, header.contractId);
                        if (ses != null) {
                           ses.sendRelayedMessage(header, buf, false);
                        }
                        break;

                     case MessageHeader.TO_ALTERNATE:
                        if (isBroadcast) {
                           broadcastAlt(sender, header, buf);
                        }
                        break;
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

   private void broadcastTopic(final EntityInfo publisher, final MessageHeader header, final ByteBuf buf) throws IOException {
      final Topic topic = publisher.getTopic(header.toId);
      if (topic != null) {
         synchronized (topic) {
            for (final Subscriber s : topic.getChildSubscribers()) {
               broadcastTopic(publisher, s, topic, header, buf);
            }
            for (final Subscriber s : topic.getProxySubscribers()) {
               broadcastTopic(publisher, s, topic, header, buf);
            }
         }
      } else {
         logger.error("Could not find topic {} for entity {}", header.toId, publisher);
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

   private void broadcastTopic(final EntityInfo publisher, final Subscriber sub, final Topic topic, final MessageHeader header,
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
   public void broadcastRegistryMessage(Message msg) {
      if (registryTopic.getNumSubscribers() > 0) {
         broadcast(msg, registryTopic);
      }
      cluster.broadcast(msg);
   }

   @Override
   public void broadcastServicesMessage(Message msg) {
      broadcast(msg, servicesTopic);
   }

   public void broadcastClusterMessage(Message msg) {
      broadcast(msg, clusterTopic);
   }

   public void broadcast(Message msg, Topic topic) {
      logger.trace("BROADCASTING {} {}", topic, msg.dump());
      if (topic != null) {
         synchronized (topic) {
            // OPTIMIZE: call broadcast() directly instead of through loop-back
            Session ses = clusterClient.getSession();
            if (ses != null) {
               ses.sendBroadcastMessage(msg, MessageHeader.TO_TOPIC, topic.topicId);
            } else {
               logger.error("broadcast failed: no session for loopback connection");
            }
         }
      }
   }

   public void broadcastAdminMessage(Message msg) {
      broadcast(msg, adminTopic);
   }

   @Override
   public void subscribe(int topicId, int entityId) {
      registry.subscribe(registry.getEntity(getEntityId()), topicId, entityId, false);
   }

   @Override
   public void unsubscribe(int topicId, int entityId) {
      registry.unsubscribe(registry.getEntity(getEntityId()), topicId, entityId, false);
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   private void scheduleHealthCheck() {
      if (!isShuttingDown()) {
         dispatcher.dispatch(1, TimeUnit.SECONDS, new Runnable() {
            public void run() {
               if (dispatcher.isRunning()) {
                  try {
                     healthCheck();
                  } catch (Throwable e) {
                     logger.error(e.getMessage(), e);
                  } finally {
                     scheduleHealthCheck();
                  }
               }
            }
         });
      }
   }

   private void healthCheck() {
      cluster.service();
      final long now = System.currentTimeMillis();
      if (now - lastStatsLog > Util.ONE_MINUTE) {
         registry.logStats();
         lastStatsLog = System.currentTimeMillis();
      }
      for (final EntityInfo e : registry.getChildren()) {
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

            // push through a dummy request to help keep dispatch pool metrics fresh
            if (e.isService()) {
               final Session ses = e.getSession();
               if (ses != null && now - ses.getLastHeardFrom() > 1153) {
                  final long t0 = System.currentTimeMillis();
                  sendRequest(new DummyRequest(), e.entityId).handle(new ResponseHandler() {
                     @Override
                     public void onResponse(Response res) {
                        final long tf = System.currentTimeMillis() - t0;
                        if (tf > 1000) {
                           logger.warn("Round trip to dispatch {} took {} ms", e, tf);
                        }
                     }
                  });
               }
            }

         }
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   public void subscribeToCluster(Session ses, int toEntityId) {
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
         cluster.sendAdminDetails(ses, toEntityId, adminTopic.topicId);
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
      //      if (ctx.session.getTheirEntityType() == Core.TYPE_TETRAPOD) {
      //         return new Error(ERROR_UNSUPPORTED);
      //      }

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
      ctx.session.sendMessage(new EntityMessage(info.entityId), MessageHeader.TO_ENTITY, Core.UNADDRESSED);

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
   public void registrySubscribe(final Session session, final int toEntityId, boolean clusterMode) {
      if (registryTopic != null) {
         synchronized (registryTopicLock) {
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
      synchronized (registryTopicLock) {
         unsubscribe(registryTopic.topicId, ctx.header.fromId);
      }
      return Response.SUCCESS;
   }

   @Override
   public Response requestServicesSubscribe(ServicesSubscribeRequest r, RequestContext ctxA) {
      SessionRequestContext ctx = (SessionRequestContext) ctxA;
      if (servicesTopic == null) {
         return new Error(ERROR_UNKNOWN);
      }
      synchronized (servicesTopicLock) {
         subscribe(servicesTopic.topicId, ctx.header.fromId);
         // send all current entities
         for (EntityInfo e : registry.getServices()) {
            ctx.session.sendMessage(new ServiceAddedMessage(e), MessageHeader.TO_ENTITY, ctx.header.fromId);
         }
      }
      return Response.SUCCESS;
   }

   @Override
   public Response requestServicesUnsubscribe(ServicesUnsubscribeRequest r, RequestContext ctx) {
      // TODO: validate 
      synchronized (servicesTopicLock) {
         unsubscribe(servicesTopic.topicId, ctx.header.fromId);
      }
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
      //      for (WebRoute r : req.routes) {
      //         webRoutes.setRoute(r.path, r.contractId, r.structId);
      //         logger.debug("Setting Web route [{}] for {}", r.path, r.contractId);
      //      } 

      cluster.registerContract(req.info);

      return Response.SUCCESS;
   }

   @Override
   protected void registerServiceInformation() {
      // do nothing, our protocol is known by all tetrapods
   }

   @Override
   public Response requestLogRegistryStats(LogRegistryStatsRequest r, RequestContext ctx) {
      registry.logStats();
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
      for (BuildCommand command : r.commands) {
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
   public Response requestGetSubscriberCount(GetSubscriberCountRequest r, RequestContext ctx) {
      EntityInfo ei = registry.getEntity(ctx.header.fromId);
      if (ei != null) {
         Topic t = ei.getTopic(r.topicId);
         if (t != null) {
            return new GetSubscriberCountResponse(t.getNumSubscribers());
         }
      }
      return Response.error(ERROR_UNKNOWN);
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
               return new GetEntityInfoResponse(e.build, e.name, s.channel.remoteAddress().getAddress().getHostAddress(), null);
            } else {
               return new GetEntityInfoResponse(e.build, e.name, null, null);
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
      if (!adminAccounts.isValidAdminRequest(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_WRITE)) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      cluster.delClusterProperty(r.key);
      return Response.SUCCESS;
   }

   @Override
   public Response requestSetClusterProperty(SetClusterPropertyRequest r, RequestContext ctx) {
      if (!adminAccounts.isValidAdminRequest(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_WRITE)) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
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
      if (!adminAccounts.isValidAdminRequest(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_WRITE)) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      if (r.def != null) {
         cluster.setWebRoot(r.def);
         return Response.SUCCESS;
      }
      return new Error(ERROR_INVALID_DATA);
   }

   @Override
   public Response requestDelWebRoot(DelWebRootRequest r, RequestContext ctx) {
      if (!adminAccounts.isValidAdminRequest(ctx, r.adminToken, Admin.RIGHTS_CLUSTER_WRITE)) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      if (r.name != null) {
         cluster.delWebRoot(r.name);
         return Response.SUCCESS;
      }
      return new Error(ERROR_INVALID_DATA);
   }

   @Override
   public Response requestLock(LockRequest r, RequestContext ctx) {
      final DistributedLock lock = cluster.getLock(r.key);
      if (lock.lock(r.leaseMillis, 10000)) {
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

      return cluster.requestUnsubscribeOwnership(r, (SessionRequestContext) ctx);
   }

}
