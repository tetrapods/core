package io.tetrapod.core;

import io.netty.buffer.ByteBuf;
import io.tetrapod.core.protocol.*;
import io.tetrapod.core.registry.*;
import io.tetrapod.core.rpc.*;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.*;

/**
 * The tetrapod service is the core cluster service which handles message routing, cluster management, service discovery, and load balancing
 * of client connections
 */
public class TetrapodService implements TetrapodContract.API, RelayHandler {
   public static final Logger         logger   = LoggerFactory.getLogger(TetrapodService.class);

   public final SecureRandom          random   = new SecureRandom();
   public final Map<Integer, Session> sessions = new ConcurrentHashMap<>();

   public final Dispatcher            dispatcher;
   public final Registry              registry;
   public final Server                privateServer;
   public final Server                publicServer;

   // TODO: Configuration
   public TetrapodService() {
      dispatcher = new Dispatcher();
      registry = new Registry(getEntityid());
      publicServer = new Server(9800, dispatcher);
      privateServer = new Server(9900, dispatcher);
   }

   public int getEntityid() {
      return 1; // FIXME -- each service needs to be issued a unique id
   }

   public void start() {
      try {
         privateServer.start();
         publicServer.start();
      } catch (Exception e) {
         // FIXME: fail service
         logger.error(e.getMessage(), e);
      }
   }

   private Session findSession(final EntityInfo entity) {
      if (entity.parentId == getEntityid()) {

      } else {
         // return session to parent registry
      }
      return null;
   }

   @Override
   public void relayRequest(final RequestHeader header, final ByteBuf in, final Session fromSession) {
      final EntityInfo entity = registry.getEntity(header.toId);
      if (entity != null) {
         final Session ses = findSession(entity);
         if (ses != null) {
            // OPTIMIZE: if we could avoid this alloc, this would be far more efficient
            final byte[] data = new byte[in.readableBytes()];
            in.readBytes(data);
            final RelayRequest req = new RelayRequest(header.structId, data);
            ses.sendRequest(header, req).handle(new ResponseHandler() {
               @Override
               public void onResponse(Response res, int errorCode) {
                  fromSession.sendResponse(res, errorCode);
               }
            });
         } else {
            logger.warn("Could not find a session for {}", entity);
         }
      } else {
         logger.warn("Could not find an entity for {}", header.toId);
      }
   }

   @Override
   public Response requestRegister(RegisterRequest r) {
      final EntityInfo info = new EntityInfo();
      info.reclaimToken = random.nextLong();
      info.build = r.build;
      info.host = "TODO";// TODO
      info.name = "TODO";// TODO
      info.type = 0; // TODO
      info.version = 0; // TODO;
      registry.register(info);
      sessions.put(info.entityId, null); // FIXME: Need RequestContext to obtain Session
      return new RegisterResponse(info.entityId, info.parentId);
   }

   @Override
   public Response genericRequest(Request r) {
      return null;
   }

}
