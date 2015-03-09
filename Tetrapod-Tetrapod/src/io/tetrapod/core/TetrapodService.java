package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.*;
import static io.tetrapod.protocol.core.CoreContract.*;
import static io.tetrapod.protocol.core.TetrapodContract.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.AdminAccounts.AdminMutator;
import io.tetrapod.core.Session.RelayHandler;
import io.tetrapod.core.registry.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.StructureAdapter;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;
import io.tetrapod.core.utils.*;
import io.tetrapod.core.web.*;
import io.tetrapod.core.web.WebRoot.FileResult;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.raft.*;
import io.tetrapod.protocol.storage.*;

import java.io.*;
import java.nio.charset.Charset;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.*;

import com.hazelcast.util.Base64;

/**
 * The tetrapod service is the core cluster service which handles message routing, cluster management, service discovery, and load balancing
 * of client connections
 */
public class TetrapodService extends DefaultService implements TetrapodContract.API, StorageContract.API, RaftContract.API, RelayHandler,
      io.tetrapod.core.registry.Registry.RegistryBroadcaster {

   public static final Logger                         logger            = LoggerFactory.getLogger(TetrapodService.class);

   private static final String                        SHARED_SECRET_KEY = "tetrapod.shared.secret";

   protected final SecureRandom                       random            = new SecureRandom();

   protected final io.tetrapod.core.registry.Registry registry;

   private Topic                                      clusterTopic;
   private Topic                                      registryTopic;
   private Topic                                      servicesTopic;

   private final Object                               registryTopicLock = new Object();

   //private final TetrapodCluster                      cluster;
   private final TetrapodWorker                       worker;

   final protected RaftStorage                        raftStorage       = new RaftStorage(this);

   private AdminAccounts                              adminAccounts;

   private final List<Server>                         servers           = new ArrayList<Server>();
   private final WebRoutes                            webRoutes         = new WebRoutes();

   private long                                       lastStatsLog;
   private ConcurrentMap<String, WebRoot>             webRootDirs       = new ConcurrentHashMap<>();

   public TetrapodService() {
      registry = new io.tetrapod.core.registry.Registry(this);
      worker = new TetrapodWorker(this);
      // cluster = new TetrapodCluster(this);
      setMainContract(new TetrapodContract());
      addContracts(new StorageContract());
      addContracts(new RaftContract());

      // add tetrapod web routes
      for (WebRoute r : contract.getWebRoutes())
         webRoutes.setRoute(r.path, r.contractId, r.structId);

      addSubscriptionHandler(new TetrapodContract.Registry(), registry);
   }

   @Override
   public void startNetwork(ServerAddress address, String token, Map<String, String> otherOpts) throws Exception {
      logger.info("***** Start Network ***** ");
      logger.info("Joining Cluster: {}", address.dump());
      this.startPaused = otherOpts.get("paused").equals("true");
      raftStorage.startListening();
      if (address.host.equals("self")) {
         raftStorage.bootstrap();
      } else {
         this.token = token;
         raftStorage.joinCluster(address);
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
         //   cluster.startListening();
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
      long count = raftStorage.getNumSessions();
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
      if (isStartingUp()) {
         // TODO: wait for confirmed cluster registry sync before calling onReadyToServe
         logger.info(" ***** READY TO SERVE ***** ");

         try {

            AuthToken.setSecret(getSharedSecret());

            adminAccounts = new AdminAccounts(raftStorage);

            // create servers
            servers.add(new Server(getHTTPPort(), new WebSessionFactory(webRootDirs, "/sockets"), dispatcher));

            // create secure port servers, if configured
            if (sslContext != null) {
               servers.add(new Server(getHTTPSPort(), new WebSessionFactory(webRootDirs, "/sockets"), dispatcher, sslContext, false));
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
         scheduleHealthCheck();
      }
   }

   @Override
   public void onShutdown(boolean restarting) {
      logger.info("Shutting Down Tetrapod");
      if (raftStorage != null) {
         raftStorage.shutdown();
      }
   }

   /**
    * This needs to be properly managed by RaftStorage
    */
   public byte[] getSharedSecret() {
      String str = Util.getProperty(SHARED_SECRET_KEY);
      return Base64.decode(str.getBytes(Charset.forName("UTF-8")));
      //      
      //      // FIXME: Move to secret.properties?
      //      String str = storage.get(SHARED_SECRET_KEY);
      //      if (str != null) {
      //         logger.info("SHARED SECRET = {}", str);
      //         return Base64.decode(str.getBytes(Charset.forName("UTF-8")));
      //      } else {
      //         byte[] b = new byte[64];
      //         Random r = new SecureRandom();
      //         r.nextBytes(b);
      //         String secret = new String(Base64.encode(b), Charset.forName("UTF-8"));
      //         storage.put(SHARED_SECRET_KEY, secret);
      //         return b;
      //      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   private Session findSession(final EntityInfo entity) {
      if (entity.parentId == getEntityId()) {
         return entity.getSession();
      } else {
         if (entity.isTetrapod()) {
            return raftStorage.getSession(entity.entityId);
         }
         final EntityInfo parent = registry.getEntity(entity.parentId);
         if (parent != null) {
            assert (parent != null);
            return raftStorage.getSession(parent.entityId);
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
      return webRoutes;
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public void broadcastRegistryMessage(Message msg) {
      if (registryTopic.getNumSubscribers() > 0) {
         broadcast(msg, registryTopic);
      }
      raftStorage.broadcast(msg);
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
               ses.sendBroadcastMessage(msg, MessageHeader.TO_TOPIC, topic.topicId);
            } else {
               logger.error("broadcast failed: no session for loopback connection");
            }
         }
      }
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
      dispatcher.dispatch(1, TimeUnit.SECONDS, new Runnable() {
         public void run() {
            if (dispatcher.isRunning()) {
               try {
                  healthCheck();
                  raftStorage.service();
               } catch (Throwable e) {
                  logger.error(e.getMessage(), e);
               }
               scheduleHealthCheck();
            }
         }
      });
   }

   private void healthCheck() {
      raftStorage.logStatus();
      final long now = System.currentTimeMillis();
      if (now - lastStatsLog > 5 * 60 * 1000) {
         registry.logStats();
         lastStatsLog = System.currentTimeMillis();
      }
      for (final EntityInfo e : registry.getChildren()) {
         if (e.isGone()) {
            if (now - e.getGoneSince() > 60 * 1000) {
               logger.info("Reaping: {}", e);
               registry.unregister(e);
            }
         } else {
            // special check for long-polling clients
            if (e.getLastContact() != null) {
               if (now - e.getLastContact() > 60 * 1000) {
                  e.setLastContact(null);
                  registry.setGone(e);
               }
            }

            // push through a dummy request to help keep dispatch pool metrics fresh
            if (e.isService()) {
               final Session ses = e.getSession();
               if (ses != null && now - ses.getLastHeardFrom() > 500) {
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

   private void subscribeToCluster(Session ses, int toEntityId) {
      assert (clusterTopic != null);
      synchronized (raftStorage) {
         subscribe(clusterTopic.topicId, toEntityId);
         raftStorage.sendClusterDetails(ses, toEntityId, clusterTopic.topicId);
      }
   }

   //   @Override
   //   public void messageClusterMember(ClusterMemberMessage m, MessageContext ctx) {
   //      synchronized (raftStorage) {
   //         if (raftStorage.addMember(m.entityId, m.host, m.servicePort, m.clusterPort, null)) {
   //            broadcast(new ClusterMemberMessage(m.entityId, m.host, m.servicePort, m.clusterPort), clusterTopic);
   //            raftStorage.addMember(m.entityId);
   //         }
   //      }
   //   }

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

      //      if (info.isTetrapod() && info.entityId == 0) {
      //         final Value<Integer> val = new Value<>();
      //         final CountDownLatch latch = new CountDownLatch(1);
      //         raftStorage.issueTetrapodId(new Callback<Integer>() {
      //            @Override
      //            public void call(Integer data) throws Exception {
      //               val.set(data);
      //               latch.countDown();
      //            }
      //         });
      //         while (latch.getCount() > 0) {
      //            try {
      //               latch.await();
      //            } catch (InterruptedException e) {}
      //         }
      //         info.entityId = val.get();
      //      }

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
   protected void registrySubscribe(final Session session, final int toEntityId, boolean clusterMode) {
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
      synchronized (servicesTopic) {
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
      for (WebRoute r : req.routes) {
         webRoutes.setRoute(r.path, r.contractId, r.structId);
         logger.debug("Setting Web route [{}] for {}", r.path, r.contractId);
      }
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

      Response res = raftStorage.requestClusterJoin(r, ctx);
      if (!res.isError()) {
         registrySubscribe(ctx.session, ctx.session.getTheirEntityId(), true);
      }
      //      SessionRequestContext ctx = (SessionRequestContext) ctxA;
      //      if (ctx.session.getTheirEntityType() != Core.TYPE_TETRAPOD) {
      //         return new Error(ERROR_INVALID_RIGHTS);
      //      }
      //
      //      ctx.session.setTheirEntityId(r.entityId);
      //
      //      logger.info("JOINING TETRAPOD {} {}", ctx.session);
      //
      //      synchronized (cluster) {
      //         if (cluster.addMember(r.entityId, r.host, r.servicePort, r.clusterPort, ctx.session)) {
      //            broadcast(new ClusterMemberMessage(r.entityId, r.host, r.servicePort, r.clusterPort), clusterTopic);
      //         }
      //      }
      //
      //      registrySubscribe(ctx.session, ctx.session.getTheirEntityId(), true);
      //
      //      return new ClusterJoinResponse(getEntityId());
      return res;
   }

   @Override
   public Response requestLogRegistryStats(LogRegistryStatsRequest r, RequestContext ctx) {
      registry.logStats();
      return Response.SUCCESS;
   }

   @Override
   public Response requestStorageGet(StorageGetRequest r, RequestContext ctx) {
      return new StorageGetResponse(raftStorage.get(r.key));
   }

   @Override
   public Response requestStorageSet(StorageSetRequest r, RequestContext ctx) {
      raftStorage.put(r.key, r.value);
      return Response.SUCCESS;
   }

   @Override
   public Response requestStorageDelete(StorageDeleteRequest r, RequestContext ctx) {
      raftStorage.delete(r.key);
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
      if (ctx.header.fromType != TYPE_ADMIN) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      final AuthToken.Decoded d = AuthToken.decodeAuthToken1(r.token);
      if (d != null) {
         final Admin admin = adminAccounts.getAdminByAccountId(d.accountId);
         if (admin != null) {
            try {
               if (adminAccounts.verifyPermission(admin, Admin.RIGHTS_USER_WRITE)) {
                  final String hash = PasswordHash.createHash(r.password);
                  final Admin newUser = adminAccounts.addAdmin(r.email.trim(), hash, r.rights);
                  if (newUser != null) {
                     return Response.SUCCESS;
                  } else {
                     // they probably already exist
                     return new Error(ERROR_INVALID_ACCOUNT);
                  }
               } else {
                  return new Error(ERROR_INVALID_RIGHTS);
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
               return new GetEntityInfoResponse(e.build, e.name, s.channel.remoteAddress().getAddress().getHostAddress());
            } else {
               return new GetEntityInfoResponse(e.build, e.name, null);
            }
         }
      }

      return Response.error(ERROR_UNKNOWN_ENTITY_ID);
   }

   /////////////// RAFT ///////////////

   @Override
   public Response requestAppendEntries(AppendEntriesRequest r, RequestContext ctx) {
      if (raftStorage != null) {
         return raftStorage.requestAppendEntries(r, ctx);
      }
      return Response.error(ERROR_NOT_CONFIGURED);
   }

   @Override
   public Response requestVote(VoteRequest r, RequestContext ctx) {
      if (raftStorage != null) {
         return raftStorage.requestVote(r, ctx);
      }
      return Response.error(ERROR_NOT_CONFIGURED);
   }

   @Override
   public Response requestInstallSnapshot(InstallSnapshotRequest r, RequestContext ctx) {
      if (raftStorage != null) {
         return raftStorage.requestInstallSnapshot(r, ctx);
      }
      return Response.error(ERROR_NOT_CONFIGURED);
   }

   @Override
   public Response requestIssueCommand(IssueCommandRequest r, RequestContext ctx) {
      if (raftStorage != null) {
         return raftStorage.requestIssueCommand(r, ctx);
      }
      return Response.error(ERROR_NOT_CONFIGURED);
   }

}
