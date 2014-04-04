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
      public Client        client;
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
   public void joinCluster(ServerAddress address) throws Exception {
      Tetrapod member = new Tetrapod();
      member.address = address;
      member.client = new Client(this);
      member.client.connect(address.host, address.port, service.getDispatcher());
      
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
      if (service.getEntityId() == 0) {
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
                  ses.sendRequest(new JoinClusterRequest(), Core.UNADDRESSED);
               }
            }
         });
      }
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

}
