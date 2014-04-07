package io.tetrapod.core;

import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.*;
import io.tetrapod.protocol.core.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
public class TetrapodCluster implements SessionFactory, Session.Listener {

   public static final Logger logger = LoggerFactory.getLogger(TetrapodCluster.class);

   public static class Tetrapod {
      public int           entityId;
      public ServerAddress address;
      public Session       session;
   }

   private final Server                 server;

   private final TetrapodService        service;

   private final Map<Integer, Tetrapod> cluster = new ConcurrentHashMap<>();

   public TetrapodCluster(TetrapodService service, Properties properties) {
      this.service = service;

      server = new Server(properties.optInt("tetrapod.cluster.port", TetrapodService.DEFAULT_CLUSTER_PORT), this);

      //      Tetrapod self = new Tetrapod();
   }

   public ServerAddress getServerAddress() {
      return new ServerAddress(Util.getHostName(), getLocalPort());
   }

   /**
    * Join an existing cluster
    */
   public void joinCluster(final ServerAddress address) throws Exception {
      Client client = new Client(this);
      client.connect(address.host, address.port, service.getDispatcher(), new Session.Listener() {

         @Override
         public void onSessionStop(Session ses) {}

         @Override
         public void onSessionStart(final Session ses) {
            ses.sendRequest(
                  new RegisterRequest(222/* FIXME */, service.token, service.getContractId(), service.getShortName(), service.status),
                  Core.UNADDRESSED).handle(new ResponseHandler() {
               @Override
               public void onResponse(Response res) {
                  if (res.isError()) {
                     service.fail("Unable to register {}", res.errorCode());
                  } else {
                     RegisterResponse r = (RegisterResponse) res;
                     ses.setMyEntityId(r.entityId);
                     ses.setTheirEntityId(r.parentId);
                     ses.setMyEntityType(Core.TYPE_TETRAPOD);
                     ses.setTheirEntityType(Core.TYPE_TETRAPOD);
                     service.registerSelf(r.entityId, service.random.nextLong());
                     addMember(r.parentId, address, ses);

                     ses.sendRequest(new JoinClusterRequest(r.entityId, getServerAddress()), Core.UNADDRESSED);
                  }
               }
            });
         }
      });
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
      ses.addSessionListener(this);
      ses.setMyEntityId(service.getEntityId());
      ses.setMyEntityType(Core.TYPE_TETRAPOD);
      ses.setTheirEntityType(Core.TYPE_TETRAPOD);
      return ses;
   }

   @Override
   public void onSessionStart(final Session ses) {

   }

   @Override
   public void onSessionStop(Session ses) {

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
   public int getLocalPort() {
      return server.getPort();
   }

   public boolean addMember(int entityId, ServerAddress address, Session ses) {
      Tetrapod pod = cluster.get(entityId);
      if (pod != null) {
         if (pod.session != null && pod.session.isConnected()) {
            return false;
         }
         synchronized (pod) {
            pod.session = ses;
         }
      } else {
         pod = new Tetrapod();
         pod.entityId = entityId;
         pod.address = address;
         pod.session = ses;
         cluster.put(entityId, pod);
      }
      return true;
   }

   public Session getSession(int entityId) {
      final Tetrapod pod = cluster.get(entityId);
      if (pod != null) {
         return pod.session;
      }
      return null;
   }

}
