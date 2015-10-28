package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.DEFAULT_DIRECT_PORT;
import static io.tetrapod.protocol.core.CoreContract.ERROR_UNKNOWN;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;

import org.slf4j.*;

/**
 * Allows a service to spawn direct connections with one another for faster RPC
 */
public class ServiceConnector implements DirectConnectionRequest.Handler, ValidateConnectionRequest.Handler {

   private static final Logger             logger            = LoggerFactory.getLogger(ServiceConnector.class);

   /**
    * The number of requests sent to a specific service that triggers us to start a direct session
    */
   private static final int                REQUEST_THRESHOLD = 100;

   private Map<Integer, DirectServiceInfo> services          = new ConcurrentHashMap<>();

   private final DefaultService            service;
   private final SSLContext                sslContext;

   private Server                          server;

   public ServiceConnector(DefaultService service, SSLContext sslContext) {
      this.service = service;
      this.sslContext = sslContext;

      int port = Util.getProperty("tetrapod.direct.port", DEFAULT_DIRECT_PORT);
      server = new Server(port, new DirectSessionFactory(Core.TYPE_ANONYMOUS, null), service.getDispatcher(), sslContext, false);
      int n = 0;
      while (true) {
         try {
            server.start(port).sync();
            return;
         } catch (Exception e) {
            port++;
            logger.info(e.getMessage());
            if (n++ >= 100) {
               throw new RuntimeException(e);
            }
         }
      }
   }

   public void shutdown() {
      server.stop();
      for (DirectServiceInfo service : services.values()) {
         service.close();
      }
   }

   private class DirectSessionFactory extends WireSessionFactory {
      private DirectSessionFactory(byte type, Session.Listener listener) {
         super(service, type, listener);
      }
   }

   /**
    * This is a wrapper class for managing a single direct connection
    */
   private class DirectServiceInfo implements Session.Listener {
      public final int    entityId;
      public final String token = String.format("%016x%016x", Util.random.nextLong(), Util.random.nextLong());
      public int          requests;
      public int          failures;
      public boolean      pending;
      public boolean      valid;
      public String       theirToken;
      public Session      ses;
      public long         restUntil;

      public DirectServiceInfo(int entityId) {
         this.entityId = entityId;
      }

      public synchronized void close() {
         pending = false;
         valid = false;
         if (ses != null) {
            ses.close();
            ses = null;
         }
      }

      /**
       * Fail our attempt to establish the connection. We won't try again for a while
       */
      private synchronized void failure() {
         failures++;
         restUntil = System.currentTimeMillis() + 1000 * failures;
         close();
      }

      @Override
      public void onSessionStop(Session ses) {
         failure();
      }

      @Override
      public void onSessionStart(Session ses) {}

      /**
       * Attempt to initiate handshake for a direct connection
       */
      protected synchronized void handshake() {
         if (System.currentTimeMillis() > restUntil) {
            pending = true;
            valid = false;
            service.clusterClient.getSession().sendRequest(new DirectConnectionRequest(token), entityId, (byte) 30).handle((res) -> {
               if (res.isError()) {
                  failure();
               } else {
                  connect((DirectConnectionResponse) res);
               }
            });
         }
      }

      private synchronized void connect(final DirectConnectionResponse res) {
         DirectConnectionResponse r = (DirectConnectionResponse) res;
         Client c = new Client(new DirectSessionFactory(Core.TYPE_SERVICE, this));
         try {
            if (sslContext != null) {
               c.enableTLS(sslContext);
            }
            c.connect(r.address.host, r.address.port, service.getDispatcher()).sync(); // FIXME: not good to be calling this while holding synchronized block
            ses = c.getSession();
            ses.setMyEntityId(service.getEntityId());
            validate(r.token);
         } catch (Exception e) {
            logger.error(e.getMessage(), e);
            failure();
         }
      }

      private synchronized void validate(String theirToken) {
         ses.sendRequest(new ValidateConnectionRequest(service.getEntityId(), theirToken), Core.DIRECT).handle((res) -> {
            if (res.isError()) {
               failure();
            } else {
               finish(((ValidateConnectionResponse) res).token);
            }
         });
      }

      private synchronized void finish(String token) {
         if (token.equals(this.token)) {
            ses.setTheirEntityId(entityId);
            ses.setTheirEntityType(Core.TYPE_SERVICE);
            pending = false;
            valid = true;
            failures = 0;
            logger.info("Direct Connection Established with {}", ses);
         } else {
            failure();
         }
      }

   }

   public DirectServiceInfo getDirectServiceInfo(int entityId) {
      synchronized (services) {
         DirectServiceInfo e = services.get(entityId);
         if (e == null) {
            e = new DirectServiceInfo(entityId);
            services.put(entityId, e);
         }
         return e;
      }
   }

   private Session getSession(Request req, int entityId, boolean isPending) {
      if (entityId != Core.DIRECT) {

         if (entityId != Core.UNADDRESSED) {
            if (entityId == service.getEntityId()) {
               if (!isPending) {
                  logger.warn("For some reason we're sending {} to ourselves", req);
               }
            } else {
               final DirectServiceInfo s = getDirectServiceInfo(entityId);
               synchronized (s) {
                  s.requests++;
                  if (s.ses != null) {
                     if (s.valid && s.ses.isConnected()) {
                        logger.trace("Sending {} direct to {}", req, s.ses);
                        return s.ses;
                     }
                  } else {
                     if (s.requests > REQUEST_THRESHOLD && !s.pending) {
                        service.dispatcher.dispatch(() -> s.handshake());
                     }
                  }
               }
            }
         }
      }
      return service.clusterClient.getSession();
   }

   public Response sendPendingRequest(final Request req, int toEntityId, final PendingResponseHandler handler) {
      if (toEntityId == Core.UNADDRESSED) {
         Entity e = service.services.getRandomAvailableService(req.getContractId());
         if (e != null) {
            toEntityId = e.entityId;
         }
      }

      final Session ses = handler.session != null ? getSession(req, toEntityId, true) : service.clusterClient.getSession();
      if (ses != service.clusterClient.getSession()) {
         logger.debug("Dispatching pending {} to {} returning on {}", req, ses, handler.session);
         final Async async = ses.sendRequest(req, toEntityId, (byte) 30);
         async.handle((res) -> {
            Response pendingRes = null;
            try {
               pendingRes = handler.onResponse(res);
            } catch (Throwable e) {
               logger.error(e.getMessage(), e);
            } finally {
               if (pendingRes != Response.PENDING) {
                  // finally return the pending response we were waiting on
                  if (pendingRes == null) {
                     pendingRes = new Error(ERROR_UNKNOWN);
                  }
                  if (!handler.sendResponse(pendingRes)) {
                     logger.error("I literally can't even ({})", pendingRes);
                  }
               } else {
                  logger.debug("Pending response returned from pending handler for {} @ {}", req, async.header.toId);
               }
            }
         });
         return Response.PENDING;
      } else {
         // send via tetrapod routing  
         return ses.sendPendingRequest(req, toEntityId, (byte) 30, handler);
      }
   }

   public Async sendRequest(Request req, int toEntityId) {
      if (toEntityId == Core.UNADDRESSED) {
         Entity e = service.services.getRandomAvailableService(req.getContractId());
         if (e != null) {
            toEntityId = e.entityId;
         }
      }

      if (toEntityId == service.getEntityId()) {
         logger.trace("Self-dispatching {}", req);
         final RequestHeader header = new RequestHeader();
         header.contractId = req.getContractId();
         header.structId = req.getStructId();
         header.toId = toEntityId;
         header.fromId = service.getEntityId();
         header.fromType = service.getEntityType();
         header.timeout = 30;
         return service.dispatchRequest(header, req, null);
      }

      final Session ses = getSession(req, toEntityId, false);
      return ses.sendRequest(req, toEntityId, (byte) 30);
   }

   @Override
   public Response genericRequest(Request r, RequestContext ctx) {
      return null;
   }

   @Override
   public Response requestValidateConnection(ValidateConnectionRequest r, RequestContext ctx) {
      final DirectServiceInfo s = getDirectServiceInfo(r.entityId);
      synchronized (s) {
         if (s.token.equals(r.token)) {
            s.ses = ((SessionRequestContext) ctx).session;
            s.ses.setTheirEntityId(r.entityId);
            s.ses.setTheirEntityType(Core.TYPE_SERVICE);
            s.valid = true;
            return new ValidateConnectionResponse(s.theirToken);
         } else {
            return new Error(CoreContract.ERROR_INVALID_TOKEN);
         }
      }
   }

   @Override
   public Response requestDirectConnection(DirectConnectionRequest r, RequestContext ctx) {
      final DirectServiceInfo s = getDirectServiceInfo(ctx.header.fromId);
      synchronized (s) {
         s.theirToken = r.token;
         return new DirectConnectionResponse(new ServerAddress(Util.getHostName(), server.getPort()), s.token);
      }
   }

}
