package io.tetrapod.core;

import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.registry.*;
import io.tetrapod.protocol.core.*;

import java.util.concurrent.TimeUnit;

import org.slf4j.*;

/**
 * Represents another Tetrapod in the cluster. This maintains a persistent connection with that tetrapod and transmits RPC for Raft
 * consensus
 */
public class TetrapodPeer implements Session.Listener, SessionFactory {
   public static final Logger   logger = LoggerFactory.getLogger(TetrapodPeer.class);

   public final TetrapodService service;
   public final int             peerId;
   public final int             entityId;
   public final String          host;
   public final int             clusterPort;

   protected int                servicePort;
   private Session              session;
   private int                  failures;
   private boolean              pendingConnect;

   public TetrapodPeer(TetrapodService service, int peerId, String host, int clusterPort) {
      this.service = service;
      this.peerId = peerId;
      this.entityId = peerId << Registry.PARENT_ID_SHIFT;
      this.host = host;
      this.clusterPort = clusterPort;
   }

   public synchronized boolean isConnected() {
      return session != null && session.isConnected();
   }

   private synchronized void setSession(Session ses) {
      this.failures = 0;
      this.session = ses;
      this.session.setTheirEntityId(entityId);
      this.session.addSessionListener(this);
      EntityInfo e = service.registry.getEntity(entityId);
      if (e != null) {
         e.setSession(ses);
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

   protected void connect() {
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
         logger.error(e.getMessage());
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
}
