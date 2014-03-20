package io.tetrapod.core;

import io.tetrapod.core.registry.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.Properties;
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

   public void serviceInit(Properties props) {
      super.serviceInit(props);
      setContract(new TetrapodContract());

      registry.setParentId(getEntityId());
      publicServer = new Server(props.optInt("tetrapod.public.port", DEFAULT_PUBLIC_PORT), this);
      privateServer = new Server(props.optInt("tetrapod.private.port", DEFAULT_PRIVATE_PORT), this);
      start();
   }

   @Override
   public int getEntityId() {
      return 1; // FIXME -- each tetrapod service needs to be issued a unique id
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
      final EntityInfo info = new EntityInfo();
      info.reclaimToken = random.nextLong();
      info.parentId = getEntityId();
      info.build = r.build;
      info.host = "TODO";// TODO
      info.name = "TODO";// TODO
      info.type = 0; // TODO
      info.version = 0; // TODO;
      registry.register(info);
      ctx.session.setEntityId(info.entityId);
      //ctx.session.setEntityType(info.type);      
      sessions.put(info.entityId, ctx.session);
      return new RegisterResponse(info.entityId, info.parentId, info.reclaimToken);
   }

}
