package io.tetrapod.core.storage;

import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.*;
import io.tetrapod.core.registry.Registry;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;

import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

import org.slf4j.*;

/**
 * Represents another Tetrapod in the cluster. This maintains a persistent connection with that tetrapod and transmits RPC for Raft
 * consensus
 */
public class TetrapodPeer implements Session.Listener, SessionFactory {
   public static final Logger   logger = LoggerFactory.getLogger(TetrapodPeer.class);

   public final TetrapodService service;
   public final int             entityId;
   public final int             peerId;
   public final String          host;
   public final int             clusterPort;

   protected int                servicePort;
   private Session              session;
   private int                  failures;
   private boolean              pendingConnect;
   private boolean              joined = false;

   public String                uuid   = null;

   public TetrapodPeer(TetrapodService service, int entityId, String host, int clusterPort, int servicePort) {
      this.service = service;
      this.entityId = entityId;
      this.host = host;
      this.clusterPort = clusterPort;
      this.servicePort = servicePort;
      this.peerId = entityId >> Registry.PARENT_ID_SHIFT;
   }

   public synchronized boolean isConnected() {
      return session != null && session.isConnected();
   }

   protected synchronized void setSession(Session ses) {
      this.failures = 0;
      this.session = ses;
      this.session.setMyEntityId(service.getEntityId());
      this.session.setTheirEntityId(entityId);
      this.session.addSessionListener(this);
      if (!joined && entityId != service.getEntityId()) {
         joinCluster();
      }
   }

   public synchronized Session getSession() {
      return session;
   }

   /**
    * Session factory for our sessions to cluster
    */
   @Override
   public Session makeSession(SocketChannel ch) {
      final Session ses = new WireSession(ch, service);
      ses.setRelayHandler(service);
      ses.setMyEntityId(service.getEntityId());
      ses.setMyEntityType(Core.TYPE_TETRAPOD);
      ses.setTheirEntityType(Core.TYPE_TETRAPOD);
      return ses;
   }

   public void connect() {
      try {
         // note: we briefly sync to make sure we don't try at the same time as another thread,  
         // but we can't hold the lock while calling sync() on the connect() call below
         synchronized (this) {
            if (pendingConnect) {
               return;
            }
            pendingConnect = true;
         }
         if (!service.isShuttingDown() && !isConnected()) {
            logger.info(" - Joining Tetrapod {} @ {}", entityId, host);
            final Client client = new Client(this);
            client.connect(host, clusterPort, service.getDispatcher()).sync();
            setSession(client.getSession());
         }
      } catch (Throwable e) {
         if (!(e instanceof ConnectException)) {
            logger.error(e.getMessage(), e);
         }
         scheduleReconnect(++failures);
      } finally {
         synchronized (this) {
            pendingConnect = false;
         }
      }
   }

   private synchronized void scheduleReconnect(int delayInSeconds) {
      if (!service.isShuttingDown()) {
         service.getDispatcher().dispatch(delayInSeconds, TimeUnit.SECONDS, new Runnable() {
            public void run() {
               connect();
            }
         });
      }
   }

   @Override
   public synchronized void onSessionStop(Session ses) {
      service.onEntityDisconnected(ses);
      scheduleReconnect(1);
   }

   @Override
   public synchronized void onSessionStart(final Session ses) {}

   @Override
   public String toString() {
      return String.format("pod[0x%08X @ %s:%d,%d]", entityId, host, servicePort, clusterPort);
   }

   private synchronized void joinCluster() {
      joined = true;
      session.sendRequest(
            new ClusterJoinRequest(service.buildNumber, service.getStatus(), Util.getHostName(), service.getEntityId(),
                  service.getServicePort(), service.getClusterPort()), Core.DIRECT).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            if (res.isError()) {
               logger.error("ClusterJoinRequest Failed {}", res);
               synchronized (TetrapodPeer.this) {
                  joined = false;
               }
            } else {
               logger.info("ClusterJoinRequest Succeeded");
            }
         }
      });
   }

}
