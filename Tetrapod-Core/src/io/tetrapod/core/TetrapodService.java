package io.tetrapod.core;

import io.tetrapod.core.registry.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.*;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.*;

/**
 * The tetrapod service is the core cluster service which handles message routing, cluster management, service discovery, and load balancing
 * of client connections
 */
public class TetrapodService extends DefaultService implements TetrapodContract.API {
   public static final Logger         logger               = LoggerFactory.getLogger(TetrapodService.class);

   public static final int            DEFAULT_PUBLIC_PORT  = 9800;
   public static final int            DEFAULT_PRIVATE_PORT = 9900;

   public final SecureRandom          random               = new SecureRandom();
   public final Map<Integer, Session> sessions             = new ConcurrentHashMap<>();

   public final Registry              registry             = new Registry();

   public Server                      privateServer;
   public Server                      publicServer;

   public TetrapodService() {
      setMainContract(new TetrapodContract());
   }

   public void startNetwork(String hostAndPort, String token) throws Exception {
      super.startNetwork(hostAndPort, token);
      if (hostAndPort == null) {
         // were not connecting anywhere, have to self register
         entityId = Token.decode(token).entityId;
         if (entityId == 0)
            throw new RuntimeException("eneitytId must be non zero to start a tetrapod");
         onRegistered();
      }
   }

   @Override
   public void onClientStart(Client client) {
      client.getSession().setEntityType(Core.TYPE_TETRAPOD);
      super.onClientStart(client);
   }

   @Override
   public void onServerStart(Server server, Session session) {
      session.setUntrusted(server == publicServer);
      super.onServerStart(server, session);
   }

   @Override
   public void onRegistered() {
      registry.setParentId(getEntityId());
      publicServer = new Server(DEFAULT_PUBLIC_PORT, this);
      privateServer = new Server(DEFAULT_PRIVATE_PORT, this);
      start();
   }

   private void start() {
      logger.info("START");
      try {
         privateServer.start();
         publicServer.start();
      } catch (Exception e) {
         // FIXME: fail service
         logger.error(e.getMessage(), e);
      }
   }

   public void stop() {
      logger.info("STOP");
      privateServer.stop();
      publicServer.stop();
   }

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
   public Response requestRegister(RegisterRequest r, RequestContext ctx) {
      EntityInfo info = null;
      Token t = Token.decode(r.token);
      if (t != null) {
         info = registry.getEntity(t.entityId);
         if (info != null) {
            if (info.reclaimToken != t.nonce) {
               info = null;
            } else {
               info.parentId = getEntityId(); // they may have changed parents
            }
         }
      }
      if (info == null) {
         info = makeInfo(r.build, t.entityId, "NAME" /* TODO */, ctx);
         registry.register(info);
      }

      ctx.session.setEntityId(info.entityId);
      ctx.session.setEntityType(info.type);
      sessions.put(info.entityId, ctx.session);
      return new RegisterResponse(info.entityId, info.parentId, Token.encode(info.entityId, info.reclaimToken));
   }

   private EntityInfo makeInfo(int build, int requestedEntityId, String name, RequestContext ctx) {
      final EntityInfo info = new EntityInfo();
      info.type = ctx.header.fromType;
      info.version = ctx.header.version;
      info.parentId = getEntityId();
      info.build = build;
      info.host = ctx.session.getChannel().remoteAddress().getHostString();
      info.name = name;
      info.reclaimToken = random.nextLong();
      if (requestedEntityId > 0 && info.type == Core.TYPE_TETRAPOD) {
         // tetrapods joining can choose their own enityId as long as it wasn't in use
         info.entityId = requestedEntityId;
      }
      return info;
   }

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

}
