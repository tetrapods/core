package io.tetrapod.web;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.*;
import io.tetrapod.core.Session.RelayHandler;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.Util;
import io.tetrapod.core.web.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.web.KeepAliveRequest;
import io.tetrapod.protocol.web.RegisterRequest;
import io.tetrapod.protocol.web.RegisterResponse;
import io.tetrapod.protocol.web.WebContract;

/**
 * The web service serves http web routes and terminates web socket connections that can relay into the cluster
 * 
 * TODO: Implement....
 * <ul>
 * <li>Message & Topic deliveries</li>
 * <li>Alt Broadcasts</li>
 * <li>Long Polling</li>
 * <li>Live subs of web roots</li>
 * <li>SetEntityReferrerRequest</li>
 * </ul>
 */
public class WebService extends DefaultService implements WebContract.API, RelayHandler, TetrapodContract.Pubsub.API {

   public static final Logger                 logger                = LoggerFactory.getLogger(WebService.class);

   public static final int                    DEFAULT_HTTP_PORT     = 8080;
   public static final int                    DEFAULT_HTTPS_PORT    = 8081;

   private final List<Server>                 servers               = new ArrayList<>();
   private final LinkedList<Integer>          clientSessionsCounter = new LinkedList<>();
   private final WebRoutes                    webRoutes             = new WebRoutes();
   private final Map<String, WebRoot>         contentRootMap        = new HashMap<>();
   private final Map<Integer, WebHttpSession> clients               = new ConcurrentHashMap<>();

   protected static final AtomicInteger       clientCounter         = new AtomicInteger();
   private long                               lastStatsLog;

   public WebService() throws IOException {
      super(new WebContract());
      addContracts(new TetrapodContract());

      addSubscriptionHandler(new TetrapodContract.Pubsub(), this);

      logger.info(" ***** WebService ***** ");

      // add the default web root
      contentRootMap.put("www", new WebRootLocalFilesystem("/", new File("www")));

      // FIXME: init contentRootMap with webRoutes

      contentRootMap.put("core",
               new WebRootLocalFilesystem("/", new File("/Users/adavidson/workspace/tetrapod/core/Tetrapod-Tetrapod/webContent")));
      contentRootMap.put("chat", new WebRootLocalFilesystem("/", new File("/Users/adavidson/workspace/tetrapod/website/webContent")));

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

      // ADD relay handler to tetrapod conn
   }

   @Override
   protected void onConnectedToCluster() {
      super.onConnectedToCluster();
      clusterClient.getSession().setRelayHandler(this);
   }

   @Override
   public void onDisconnectedFromCluster() {
      // TODO
   }

   // Pause will close the HTTP and HTTPS ports on the web service
   @Override
   public void onPaused() {
      for (Server httpServer : servers) {
         httpServer.close();
      }
   }

   // Purge will boot all non-admin sessions from the web service
   @Override
   public void onPurged() {
      for (Server httpServer : servers) {
         httpServer.purge();
      }
   }

   // UnPause will restart the HTTP listeners on the web service.
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
      final WebHttpSession ses = new WebHttpSession(ch, this, contentRootMap, "/sockets");
      ses.setRelayHandler(this);
      ses.setMyEntityId(getEntityId());
      ses.setMyEntityType(Core.TYPE_SERVICE);
      ses.setTheirEntityType(Core.TYPE_CLIENT);
      ses.addSessionListener(new Session.Listener() {
         @Override
         public void onSessionStop(Session ses) {
            logger.debug("Web Session Stopped: {}", ses);
            clients.remove(ses.getTheirEntityId());
         }

         @Override
         public void onSessionStart(Session ses) {}
      });
      return ses;
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public int getAvailableService(int contractId) {
      Entity e = services.getRandomAvailableService(contractId);
      if (e != null) {
         return e.entityId;
      }
      return 0;
   }

   @Override
   public Session getRelaySession(int entityId, int contractId) {
      if (entityId == parentId) {
         return clusterClient.getSession();
      }

      Entity entity = null;
      if (entityId == Core.UNADDRESSED) {
         entity = services.getRandomAvailableService(contractId);
      } else {
         entity = services.getEntity(entityId);
      }
      if (entity != null) {
         if (entity.entityId == parentId) {
            return clusterClient.getSession();
         }
         return serviceConnector.getDirectServiceInfo(entityId).getSession();
      }
      return null;
   }

   @Override
   public void relayMessage(MessageHeader header, ByteBuf buf, boolean isBroadcast) throws IOException {
      if (isBroadcast) {
         final ServiceTopic topic = topics.get(topicKey(header.fromId, header.topicId));
         if (topic != null) {
            synchronized (topic) {
               for (final Subscriber s : topic.getSubscribers()) {
                  WebHttpSession ses = clients.get(s.entityId);
                  if (ses != null) {
                     ses.sendRelayedMessage(header, buf, isBroadcast);
                  }
               }
            }
         } else {
            logger.warn("Could not find topic {} for entity {} : {}", header.topicId, header.fromId, header.dump());
            sendMessage(new TopicNotFoundMessage(header.fromId, header.topicId), header.fromId, 0);
         }
      } else {
         WebHttpSession ses = clients.get(header.toChildId);
         if (ses != null) {
            ses.sendRelayedMessage(header, buf, isBroadcast);
         }
      }
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

   @Override
   public Response requestRegister(RegisterRequest r, RequestContext ctxA) {
      final SessionRequestContext ctx = (SessionRequestContext) ctxA;
      final int entityId = clientCounter.incrementAndGet();
      ctx.session.setTheirEntityId(entityId);
      return new RegisterResponse(entityId, getEntityId());
   }

   @Override
   public Response requestKeepAlive(KeepAliveRequest r, RequestContext ctx) {
      return Response.SUCCESS;
   }

   //////////////////////////////////////////////////////////////////////////////////////////

   private final Map<Long, ServiceTopic> topics = new ConcurrentHashMap<>();

   @Override
   public void genericMessage(Message message, MessageContext ctx) {}

   public long topicKey(int publisherId, int topicId) {
      return ((long) (publisherId) << 32) | topicId;
   }

   @Override
   public void messageTopicPublished(final TopicPublishedMessage m, MessageContext ctx) {
      logger.debug("******* {} {}", ctx.header.dump(), m.dump());
      final ServiceTopic topic = topics.get(topicKey(m.publisherId, m.topicId));
      if (topic == null) {
         topics.put(topicKey(m.publisherId, m.topicId), new ServiceTopic(m.publisherId, m.topicId));
         //topic.queue(() -> owner.publish(m.topicId)); // FIXME
      } else {
         logger.error("Publisher {} already exists?", ctx.header.fromId);
      }
   }

   @Override
   public void messageTopicUnpublished(final TopicUnpublishedMessage m, MessageContext ctx) {
      logger.debug("******* {} {}", ctx.header.dump(), m.dump());
      final ServiceTopic topic = topics.get(topicKey(m.publisherId, m.topicId));
      if (topic != null) {
         synchronized (topic) {
            for (Subscriber sub : topic.getSubscribers().toArray(new Subscriber[0])) {
               if (topic.unsubscribe(sub.entityId, true)) {
                  final Session s = clients.get(sub.entityId);
                  if (s != null) {
                     //FIXME: s.unsubscribe(topic);
                     // notify the subscriber that they have been unsubscribed from this topic
                     s.sendMessage(new TopicUnsubscribedMessage(m.publisherId, topic.topicId, entityId, sub.entityId), entityId,
                              sub.entityId);
                  }
               }
            }
         }
         //topic.queue(() -> unpublish(owner, m.topicId)); // TODO: kick()
      } else {
         logger.info("Could not find publisher entity {}", ctx.header.fromId);
      }
   }

   @Override
   public void messageTopicSubscribed(final TopicSubscribedMessage m, MessageContext ctx) {
      logger.debug("******* {} {}", ctx.header.dump(), m.dump());
      final ServiceTopic topic = topics.get(topicKey(m.publisherId, m.topicId));
      if (topic != null) {
         topic.subscribe(m.childId, m.once);
         //FIXME: s.subscribe
         // topic.queue(() -> subscribe(owner, m.topicId, m.entityId, m.childId, m.once)); // TODO: kick() 
      } else {
         logger.info("Could not find publisher entity {}", ctx.header.fromId);
      }
   }

   @Override
   public void messageTopicUnsubscribed(final TopicUnsubscribedMessage m, MessageContext ctx) {
      logger.debug("******* {} {}", ctx.header.dump(), m.dump());
      final ServiceTopic topic = topics.get(topicKey(m.publisherId, m.topicId));
      if (topic != null) {
         if (topic.unsubscribe(entityId, true)) {
            final Session s = clients.get(m.childId);
            if (s != null) {
               //FIXME: e.unsubscribe(topic);
               // notify the subscriber that they have been unsubscribed from this topic
               s.sendMessage(new TopicUnsubscribedMessage(m.publisherId, topic.topicId, entityId, m.childId), entityId, m.childId);
            }
         }

         //topic.queue(() -> unsubscribe(owner, m.topicId, m.entityId, m.childId, false)); // TODO: kick()
      } else {
         logger.info("Could not find publisher entity {}", ctx.header.fromId);
      }
   }

   // FIXME: call when client disconnects / service unregisters

   //   private void clearAllTopicsAndSubscriptions(final EntityInfo e) {
   //      logger.debug("clearAllTopicsAndSubscriptions: {}", e);
   //      // Unpublish all their topics
   //      for (RegistryTopic topic : e.getTopics()) {
   //         unpublish(e, topic.topicId);
   //      }
   //      // Unsubscribe from all subscriptions we're managing
   //      for (RegistryTopic topic : e.getSubscriptions()) {
   //         EntityInfo owner = getEntity(topic.ownerId);
   //         // assert (owner != null); 
   //         if (owner != null) {
   //            unsubscribe(owner, topic.topicId, e.entityId, true);
   //
   //            // notify the publisher that this client's subscription is now dead
   //            broadcaster.sendMessage(new TopicUnsubscribedMessage(owner.entityId, topic.topicId, e.entityId), owner.entityId);
   //
   //         } else {
   //            // bug here cleaning up topics on unreg, I think...
   //            logger.warn("clearAllTopicsAndSubscriptions: Couldn't find {} owner {}", topic, topic.ownerId);
   //         }
   //      }
   //      cluster.getPublisher().unsubscribeFromAllTopics(e.entityId);
   //   }

}
