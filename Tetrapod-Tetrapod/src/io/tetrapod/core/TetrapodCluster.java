package io.tetrapod.core;

import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.registry.EntityInfo;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.*;

/**
 * Manages a tetrapod's set of connections to the rest of the tetrapod cluster.
 * 
 * <ul>
 * <li>Tracks all known Tetrapods</li>
 * <li>Maintains full set of connections to cluster members</li>
 * <li>Synchronizes registry state with tetrapod cluster</li>
 * </ul>
 */
public class TetrapodCluster implements SessionFactory {

   public static final Logger           logger  = LoggerFactory.getLogger(TetrapodCluster.class);

   private final Server                 server;

   private final TetrapodService        service;

   private final Map<Integer, Tetrapod> cluster = new ConcurrentHashMap<>();

   private Session                      pendingSession;

   public TetrapodCluster(TetrapodService service) {
      this.service = service;
      server = new Server(service.getClusterPort(), this, service.getDispatcher());
   }

   public ServerAddress getServerAddress() {
      return new ServerAddress(Util.getHostName(), getClusterPort());
   }

   /**
    * Join an existing cluster
    */
   public boolean joinCluster(final ServerAddress address) throws Exception {
      final Client client = new Client(this);
      client.connect(address.host, address.port, service.getDispatcher(), new Session.Listener() {

         @Override
         public void onSessionStop(Session ses) {}

         @Override
         public void onSessionStart(final Session ses) {
            ses.sendRequest(
                  new RegisterRequest(service.buildNumber, service.token, service.getContractId(), service.getShortName(), service.status,
                        Util.getHostName()), Core.DIRECT).handle(new ResponseHandler() {
               @Override
               public void onResponse(Response res) {
                  if (res.isError()) {
                     service.fail("Unable to register: " + res.errorCode());
                  } else {
                     RegisterResponse r = (RegisterResponse) res;
                     ses.setMyEntityId(r.entityId);
                     ses.setTheirEntityId(r.parentId);
                     setPendingSession(ses);
                     service.registerSelf(r.entityId, service.random.nextLong());
                  }
               }
            });
         }
      }).sync();
      return true;
   }

   public void startListening() throws IOException {
      try {
         server.start().sync();
      } catch (InterruptedException e) {
         throw new IOException(e);
      }
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

   /**
    * Gracefully shutdown and leave the cluster
    */
   public void shutdown() {
      server.stop();
   }

   /**
    * Get the port we are listening on
    */
   public int getClusterPort() {
      return server.getPort();
   }

   public Collection<Tetrapod> getMembers() {
      return cluster.values();
   }

   public synchronized Session getPendingSession() {
      return pendingSession;
   }

   public synchronized void setPendingSession(Session pendingSession) {
      this.pendingSession = pendingSession;
   }

   public Session getSession(int entityId) {
      final Tetrapod pod = cluster.get(entityId);
      if (pod != null) {
         return pod.getSession();
      }
      return null;
   }

   /**
    * Scan our list of known tetrapods and establish a connection to any we are missing
    */
   public void service() {
      if (service.getEntityId() != 0) {
         for (Tetrapod pod : cluster.values()) {
            if (pod.entityId != service.getEntityId()) {
               if (pod.getSession() == null) {
                  pod.connect();
               }
            }
         }
      }
   }

   public boolean addMember(int entityId, String host, int servicePort, int clusterPort, Session ses) {

      // ignore ourselves
      if (entityId == service.getEntityId()) {
         return false;
      }

      // if session is null, we might have a session already from our first cluster connection
      if (ses == null && getPendingSession() != null && getPendingSession().getTheirEntityId() == entityId) {
         ses = getPendingSession();
      }

      Tetrapod pod = cluster.get(entityId);
      if (pod != null) {
         if (pod.isConnected()) {
            return false;
         }
      } else {
         pod = new Tetrapod(entityId, host, servicePort, clusterPort);
         cluster.put(entityId, pod);
         logger.info(" * ADDING TETRAPOD CLUSTER MEMBER: {} @ {}", pod, ses);
      }

      if (ses != null) {
         pod.setSession(ses);
      }
      return true;
   }

   public class Tetrapod implements Session.Listener {
      public final int    entityId;
      public final String host;
      public final int    servicePort;
      public final int    clusterPort;
      private Session     session;
      private int         failures;

      //private boolean     synced = false;

      public Tetrapod(int entityId, String host, int servicePort, int clusterPort) {
         this.entityId = entityId;
         this.host = host;
         this.servicePort = servicePort;
         this.clusterPort = clusterPort;
      }

      public synchronized boolean isConnected() {
         return session != null && session.isConnected();
      }

      private synchronized void setSession(Session ses) {
         // this.synced = false;
         this.failures = 0;
         this.session = ses;
         this.session.setTheirEntityId(entityId);
         this.session.sendRequest(
               new ClusterJoinRequest(service.getEntityId(), service.getHostName(), service.getServicePort(), getClusterPort()),
               Core.DIRECT).log();
         this.session.addSessionListener(this);
         EntityInfo e = service.registry.getEntity(entityId);
         if (e != null) {
            e.setSession(ses);
         }
      }

      public synchronized Session getSession() {
         return session;
      }

      private synchronized void connect() {
         try {
            if (!service.isShuttingDown() && !isConnected()) {
               logger.info(" - Joining Tetrapod {} @ {}", entityId, host);
               final Client client = new Client(TetrapodCluster.this);
               client.connect(host, clusterPort, service.getDispatcher()).addListener(new ChannelFutureListener() {
                  @Override
                  public void operationComplete(ChannelFuture future) throws Exception {
                     if (!future.isSuccess()) {
                        scheduleReconnect(Math.max(++failures, 30));
                     } else {
                        setSession(client.getSession());
                     }
                  }
               });
            }
         } catch (Throwable e) {
            logger.error(e.getMessage());
            scheduleReconnect(++failures);
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

   protected void broadcast(Message msg) {
      for (Tetrapod pod : getMembers()) {
         if (pod.entityId != service.getEntityId()) {
            if (pod.isConnected()) {
               Session ses = pod.getSession();
               ses.sendMessage(msg, ses.getTheirEntityId(), Core.UNADDRESSED);
            }
         }
      }
   }

   protected void sendClusterDetails(Session ses, int toEntityId, int topicId) {
      // send ourselves
      ses.sendMessage(new ClusterMemberMessage(service.getEntityId(), service.getHostName(), service.getServicePort(), getClusterPort()),
            toEntityId, topicId);
      // send all current members
      for (Tetrapod pod : cluster.values()) {
         ses.sendMessage(new ClusterMemberMessage(pod.entityId, pod.host, pod.servicePort, pod.clusterPort), toEntityId, topicId);
      }
   }

   public int getNumSessions() {
      return server.getNumSessions();
   }

}
