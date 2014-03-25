package io.tetrapod.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.Session.RelayHandler;
import io.tetrapod.core.registry.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.utils.Util;
import io.tetrapod.core.web.WebRoutes;
import io.tetrapod.protocol.core.*;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.*;

import org.slf4j.*;

/**
 * The tetrapod service is the core cluster service which handles message routing, cluster
 * management, service discovery, and load balancing of client connections
 */
public class TetrapodService extends DefaultService implements TetrapodContract.API, RelayHandler, Registry.RegistryBroadcaster {
   public static final Logger          logger               = LoggerFactory.getLogger(TetrapodService.class);

   public static final int             DEFAULT_PUBLIC_PORT  = 9900;
   public static final int             DEFAULT_SERVICE_PORT = 9901;
   public static final int             DEFAULT_CLUSTER_PORT = 9902;

   private final SecureRandom          random               = new SecureRandom();
   private final Map<Integer, Session> sessions             = new ConcurrentHashMap<>();

   public final Registry               registry;

   private Topic                       registryTopic;

   private Server                      clusterServer;
   private Server                      serviceServer;
   private Server                      publicServer;

   private final WebRoutes             webRoutes            = new WebRoutes();

   public TetrapodService() {
      registry = new Registry(this);
      setMainContract(new TetrapodContract());
   }

   @Override
   public void startNetwork(String hostAndPort, String token) throws Exception {

      publicServer = new Server(DEFAULT_PUBLIC_PORT, new ServerSessionFactory(publicServer));
      serviceServer = new Server(DEFAULT_SERVICE_PORT, new ServerSessionFactory(serviceServer));
      clusterServer = new Server(DEFAULT_CLUSTER_PORT, new ServerSessionFactory(clusterServer));

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
      final EntityInfo e = new EntityInfo(entityId, 0, random.nextLong(), Util.getHostName(), 0, Core.TYPE_TETRAPOD, getShortName(), 0, 0);
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
      // is there a better way to set this message dispatch handlers?
      addMessageHandler(TetrapodContract.CONTRACT_ID, registry);
   }

   public byte getEntityType() {
      return Core.TYPE_TETRAPOD;
   }

   private class ServerSessionFactory implements SessionFactory {
      private final Server server;

      private ServerSessionFactory(Server server) {
         this.server = server;
      }

      /**
       * Session factory for our sessions from clients and services
       */
      @Override
      public Session makeSession(SocketChannel ch) {
         final Session ses = new WireSession(ch, TetrapodService.this);
         ses.setMyEntityId(getEntityId());
         ses.setMyEntityType(Core.TYPE_TETRAPOD);

         ses.setTheirEntityType(Core.TYPE_ANONYMOUS);
         if (server == clusterServer) {
            ses.setTheirEntityType(Core.TYPE_TETRAPOD);
         } else if (server == serviceServer) {
            ses.setTheirEntityType(Core.TYPE_SERVICE);
         }

         ses.setRelayHandler(TetrapodService.this);
         ses.addSessionListener(new Session.Listener() {
            @Override
            public void onSessionStop(Session ses) {
               // TODO: stuff, set this entity as GONE, etc...
               registry.updateStatus(ses.getTheirEntityId(), status | Core.STATUS_GONE);
               // TODO: set all children to gone as well
            }

            @Override
            public void onSessionStart(Session ses) {
               // TODO: stuff
            }
         });
         return ses;
      }
   }

   @Override
   public void onRegistered() {
      registryTopic = registry.publish(entityId);
   }

   /**
    * As a Tetrapod service, we can't start serving as one until we've registered & fully sync'ed
    * with the cluster, or self-registered if we are the first one. We call this once this criteria
    * has been reached
    */
   private void onReadyToServe() {
      try {
         serviceServer.start().sync();
         publicServer.start().sync();
      } catch (Exception e) {
         fail(e);
      }

      scheduleHealthCheck();
      // TODO: at this point we can clear the INIT status or some-such...
   }

   public void stop() {
      logger.info("STOP");
      publicServer.stop();
      serviceServer.stop();
      clusterServer.stop();
   }

   // ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   private Session findSession(final EntityInfo entity) {
      if (entity.parentId == getEntityId()) {
         return sessions.get(entity.entityId);
      } else {
         return sessions.get(entity.parentId);
      }
   }

   @Override
   public WireSession getRelaySession(int entityId, int contractId) {
      final EntityInfo entity = registry.getEntity(entityId);
      if (entity != null) {
         return (WireSession) findSession(entity);
      } else {
         logger.warn("Could not find an entity for {}", entityId);
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
            synchronized (topic) { // FIXME: Won't need this sync if all topics processed on same
                                   // thread
               for (Subscriber s : topic.getSubscribers()) {
                  final EntityInfo e = registry.getEntity(s.entityId);
                  if (e != null) {
                     if (e.parentId == getEntityId() || e.isTetrapod()) {
                        WireSession session = (WireSession) findSession(e);
                        if (session != null) {
                           session.forwardMessage(header, buf);
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
      logger.info("BROADCASTING {} {}", registryTopic, msg.dump());
      if (registryTopic != null)
      sendMessage(msg, 0, registryTopic.topicId);
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
      dispatcher.dispatch(10, TimeUnit.SECONDS, new Runnable() {
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
      registry.logStats();
   }

   // ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public Response requestRegister(RegisterRequest r, RequestContext ctx) {
      if (getEntityId() == 0) {
         return new Error(Core.ERROR_SERVICE_UNAVAILABLE);
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
      }

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
      sessions.put(info.entityId, ctx.session);

      return new RegisterResponse(info.entityId, info.parentId, Token.encode(info.entityId, info.reclaimToken));
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
               return new Error(PublishRequest.ERROR_NOT_PARENT);
            }
         } else {
            return new Error(PublishRequest.ERROR_INVALID_ENTITY);
         }
      }
      return new Error(Core.ERROR_INVALID_RIGHTS);
   }

   @Override
   public Response requestRegistrySubscribe(RegistrySubscribeRequest r, RequestContext ctx) {
      sendMessage(new TopicSubscribedMessage(registryTopic.ownerId, registryTopic.topicId, ctx.header.fromId), 0, 0);
      return Response.SUCCESS;
   }

   @Override
   public Response requestServiceStatusUpdate(ServiceStatusUpdateRequest r, RequestContext ctx) {
      // TODO: don't allow certain bits to be set from a request
      registry.updateStatus(ctx.header.fromId, r.status);
      return Response.SUCCESS;
   }

   @Override
   public Response requestAddWebRoutes(AddWebRoutesRequest req, RequestContext ctx) {
      for (WebRoute r : req.routes)
         webRoutes.setRoute(r.path, r.contractId, r.structId);
      return Response.SUCCESS;
   }
   

}
