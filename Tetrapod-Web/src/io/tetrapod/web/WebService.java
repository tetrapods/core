package io.tetrapod.web;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.*;
import io.tetrapod.core.Session.RelayHandler;
import io.tetrapod.core.utils.Util;
import io.tetrapod.core.web.*;
import io.tetrapod.protocol.core.Core;
import io.tetrapod.protocol.core.MessageHeader;
import io.tetrapod.protocol.web.WebContract;

/**
 * The web service serves http web routes and terminates web socket connections that can relay into the cluster
 */
public class WebService extends DefaultService implements WebContract.API, RelayHandler {

   public static final Logger         logger                = LoggerFactory.getLogger(WebService.class);

   public static final int            DEFAULT_HTTP_PORT     = 8080;
   public static final int            DEFAULT_HTTPS_PORT    = 8081;

   private final List<Server>         servers               = new ArrayList<>();
   private final LinkedList<Integer>  clientSessionsCounter = new LinkedList<>();
   private final WebRoutes            webRoutes             = new WebRoutes();
   private final Map<String, WebRoot> contentRootMap        = new HashMap<>();
   private long                       lastStatsLog;

   public WebService() throws IOException {
      super(new WebContract());
      logger.info(" ***** WebService ***** ");

      // add the default web root
      contentRootMap.put("www", new WebRootLocalFilesystem("/", new File("www")));
   }

   @Override
   public String getServiceIcon() {
      return "fa-group";
   }

   @Override
   public long getCounter() {
      return getNumActiveClients();
   }

   @Override
   public void onReadyToServe() {
      logger.info(" ***** READY TO SERVE ***** ");
      if (isStartingUp()) {
         try {
            // FIXME: init contentRootMap 

            servers.add(new Server(Util.getProperty("tetrapod.http.port", DEFAULT_HTTP_PORT), (ch) -> makeWebSession(ch), dispatcher));
            // create secure port servers, if configured
            if (sslContext != null) {
               servers.add(new Server(Util.getProperty("tetrapod.https.port", DEFAULT_HTTPS_PORT), (ch) -> makeWebSession(ch), dispatcher,
                        sslContext, false));
            }
            // start listening
            for (Server s : servers) {
               s.start().sync();
            }
            scheduleHealthCheck();
         } catch (Exception e) {
            fail(e);
         }
      }
   }

   @Override
   public void onDisconnectedFromCluster() {

   }

   // Pause will close the HTTP and HTTPS ports on the tetrapod service
   @Override
   public void onPaused() {
      for (Server httpServer : servers) {
         httpServer.close();
      }
   }

   // Purge will boot all non-admin sessions from the tetrapod service
   @Override
   public void onPurged() {
      for (Server httpServer : servers) {
         httpServer.purge();
      }
   }

   // UnPause will restart the HTTP listeners on the tetrapod service.
   @Override
   public void onUnpaused() {
      for (Server httpServer : servers) {
         try {
            httpServer.start().sync();
         } catch (Exception e) {
            fail(e);
         }
      }
   }

   @Override
   public void onShutdown(boolean restarting) {
      for (Server httpServer : servers) {
         httpServer.close();
      }
   }

   public Session makeWebSession(SocketChannel ch) {
      final Session ses = new WebHttpSession(ch, this, contentRootMap, "/sockets");
      ses.setRelayHandler(this);
      ses.setMyEntityId(getEntityId());
      ses.setMyEntityType(Core.TYPE_TETRAPOD);
      ses.setTheirEntityType(Core.TYPE_CLIENT);
      ses.addSessionListener(new Session.Listener() {
         @Override
         public void onSessionStop(Session ses) {
            logger.debug("Session Stopped: {}", ses);
         }

         @Override
         public void onSessionStart(Session ses) {}
      });
      return ses;
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public int getAvailableService(int contractid) {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public Session getRelaySession(int toId, int contractid) {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public void relayMessage(MessageHeader header, ByteBuf buf, boolean isBroadcast) throws IOException {
      // TODO Auto-generated method stub

   }

   @Override
   public WebRoutes getWebRoutes() {
      return webRoutes;
   }

   @Override
   public boolean validate(int entityId, long token) {
      return false;
   }
   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   private void scheduleHealthCheck() {
      if (!isShuttingDown()) {
         dispatcher.dispatch(1, TimeUnit.SECONDS, () -> {
            if (dispatcher.isRunning()) {
               try {
                  healthCheck();
               } catch (Throwable e) {
                  logger.error(e.getMessage(), e);
               } finally {
                  scheduleHealthCheck();
               }
            }
         });
      }
   }

   private void healthCheck() {
      final long now = System.currentTimeMillis();
      if (now - lastStatsLog > Util.ONE_MINUTE) {
         lastStatsLog = System.currentTimeMillis();

         final int clients = getNumActiveClients();
         synchronized (clientSessionsCounter) {
            clientSessionsCounter.addLast(clients);
            if (clientSessionsCounter.size() > 1440) {
               clientSessionsCounter.removeFirst();
            }
         }
      }

      // TODO: Terminate long polling clients that haven't checked in

      //      // for all of our clients:
      //      for (final EntityInfo e : registry.getChildren()) {
      //         // special check for long-polling clients
      //         if (e.getLastContact() != null) {
      //            if (now - e.getLastContact() > Util.ONE_MINUTE) {
      //               e.setLastContact(null);
      //               registry.setGone(e);
      //            }
      //         }
      //      }

   }

   private int getNumActiveClients() {
      return servers.stream().mapToInt(Server::getNumSessions).sum();
   }

}
