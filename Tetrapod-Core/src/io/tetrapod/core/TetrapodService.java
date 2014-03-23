package io.tetrapod.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.Session.RelayHandler;
import io.tetrapod.core.registry.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.*;

import org.slf4j.*;

/**
 * The tetrapod service is the core cluster service which handles message routing, cluster management, service discovery, and load balancing
 * of client connections
 */
public class TetrapodService extends DefaultService implements TetrapodContract.API, RelayHandler, Registry.RegistryBroadcaster {
   public static final Logger          logger               = LoggerFactory.getLogger(TetrapodService.class);

   public static final int             DEFAULT_PUBLIC_PORT  = 9800;
   public static final int             DEFAULT_PRIVATE_PORT = 9900;

   private final SecureRandom          random               = new SecureRandom();
   private final Map<Integer, Session> sessions             = new ConcurrentHashMap<>();

   public final Registry               registry;

   private Topic                       registryTopic;

   private Server                      privateServer;
   private Server                      publicServer;

   public TetrapodService() {
      registry = new Registry(this);
      setMainContract(new TetrapodContract());
      addPeerContracts(new TetrapodContract.Registry());
   }

   @Override
   public void startNetwork(String hostAndPort, String token) throws Exception {
      this.token = token;
      if (hostAndPort == null) {
         // were not connecting anywhere, have to self register
         this.entityId = registry.setParentId(1);

         final EntityInfo e = new EntityInfo(entityId, 0, random.nextLong(), Util.getHostName(), 0, Core.TYPE_TETRAPOD, getShortName(), 0,
               0);
         registry.register(e);
         logger.info(String.format("I AM THE FIRST: 0x%08X %s", entityId, e));
         onRegistered();
         onReadyToServe();
      } else {
         // TODO: Join existing cluster via non-default mechanism
      }
   }

   @Override
   public void onConnectedToCluster() {
      logger.info("Connected to Self");
      cluster.getSession().setMyEntityId(entityId);
      cluster.getSession().setTheirEntityId(entityId);
      cluster.getSession().setMyEntityType(Core.TYPE_TETRAPOD);
      cluster.getSession().setTheirEntityType(Core.TYPE_TETRAPOD);
   }

   public byte getEntityType() {
      return Core.TYPE_TETRAPOD;
   }

   private class TrustedSessionFactory implements SessionFactory {
      private final boolean trusted;

      private TrustedSessionFactory(boolean trusted) {
         this.trusted = trusted;
      }

      /**
       * Session factory for our sessions from clients and services
       */
      @Override
      public Session makeSession(SocketChannel ch) {
         final Session ses = new Session(ch, TetrapodService.this);
         ses.setMyEntityId(getEntityId());
         ses.setMyEntityType(Core.TYPE_TETRAPOD);
         ses.setTheirEntityType(trusted ? Core.TYPE_SERVICE : Core.TYPE_CLIENT);
         ses.setRelayHandler(TetrapodService.this);
         ses.addSessionListener(new Session.Listener() {
            @Override
            public void onSessionStop(Session ses) {
               // TODO: stuff, set this entity as GONE, etc...
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
    * As a Tetrapod service, we can't start serving as one until we've registered & fully sync'ed with the cluster, or self-registered if we
    * are the first one. We call this once this criteria has been reached
    */
   private void onReadyToServe() {
      publicServer = new Server(DEFAULT_PUBLIC_PORT, new TrustedSessionFactory(false));
      privateServer = new Server(DEFAULT_PRIVATE_PORT, new TrustedSessionFactory(true));
      logger.info("START");
      try {
         privateServer.start();
         publicServer.start();
         super.startNetwork("localhost:" + DEFAULT_PRIVATE_PORT, token);
      } catch (Exception e) {
         // FIXME: fail service
         logger.error(e.getMessage(), e);
      }

      scheduleHealthCheck();
      // TODO: at this point we can clear the INIT status or some-such...
   }

   public void stop() {
      logger.info("STOP");
      privateServer.stop();
      publicServer.stop();
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   private Session findSession(final EntityInfo entity) {
      if (entity.parentId == getEntityId()) {
         return sessions.get(entity.entityId);
      } else {
         return sessions.get(entity.parentId);
      }
   }

   @Override
   public Session getRelaySession(int entityId) {
      final EntityInfo entity = registry.getEntity(entityId);
      if (entity != null) {
         return findSession(entity);
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
            synchronized (topic) { // FIXME: Won't need this sync if all topics processed on same thread
               for (Subscriber s : topic.getSubscribers()) {
                  final EntityInfo e = registry.getEntity(s.entityId);
                  if (e != null) {
                     if (e.parentId == getEntityId() || e.isTetrapod()) {
                        final Session session = findSession(e);
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

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public void broadcastRegistryMessage(Message msg) {
      sendMessage(msg, 0, registryTopic.topicId);
      logger.info("BROADCASTING {} {}", registryTopic, msg.dump());
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

   //////////////////////////////////////////////////////////////////////////////////////////

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

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public Response requestRegister(RegisterRequest r, RequestContext ctx) {
      EntityInfo info = null;
      final Token t = Token.decode(r.token);
      if (t != null) {
         info = registry.getEntity(t.entityId);
         if (info != null) {
            if (info.reclaimToken != t.nonce) {
               info = null;
            }
         }
      }
      if (info == null) {
         info = new EntityInfo();
         info.version = ctx.header.version;
         info.build = r.build;
         info.host = ctx.session.getChannel().remoteAddress().getHostString();
         info.name = r.name;
         info.reclaimToken = random.nextLong();
         registry.register(info);
      }

      info.parentId = getEntityId();
      info.type = ctx.session.getTheirEntityType();
      if (info.type == Core.TYPE_ANONYMOUS) {
         info.type = Core.TYPE_CLIENT;
      }

      ctx.session.setTheirEntityId(info.entityId);
      ctx.session.setTheirEntityType(info.type);
      sessions.put(info.entityId, ctx.session);
      return new RegisterResponse(info.entityId, info.parentId, Token.encode(info.entityId, info.reclaimToken));
   }

   @Override
   public Response requestPublish(PublishRequest r, RequestContext ctx) {
      if (ctx.header.fromType == Core.TYPE_TETRAPOD || ctx.header.fromType == Core.TYPE_SERVICE) {
         final Topic t = registry.publish(ctx.header.fromId);
         if (t != null) {
            return new PublishResponse(t.topicId);
         }
      }
      return new Error(Core.ERROR_INVALID_RIGHTS);
   }

   @Override
   public Response requestRegistrySubscribe(RegistrySubscribeRequest r, RequestContext ctx) {
      sendMessage(new TopicSubscribedMessage(registryTopic.ownerId, registryTopic.topicId, ctx.header.fromId), 0, 0);
      return Response.SUCCESS;
   }

}
