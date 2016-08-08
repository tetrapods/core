package io.tetrapod.web;

import static io.tetrapod.protocol.core.CoreContract.ERROR_INVALID_ENTITY;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.*;
import io.tetrapod.core.ServiceConnector.DirectServiceInfo;
import io.tetrapod.core.Session.RelayHandler;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.StructureAdapter;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;
import io.tetrapod.core.utils.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.web.*;
import io.tetrapod.protocol.web.RegisterRequest;
import io.tetrapod.protocol.web.RegisterResponse;

/**
 * The web service serves http web routes and terminates web socket connections that can relay into the cluster
 */
public class WebService extends DefaultService
      implements WebContract.API, RelayHandler, TetrapodContract.Pubsub.API, TetrapodContract.Services.API {

   public static final Logger                 logger                = LoggerFactory.getLogger(WebService.class);

   public static final int                    DEFAULT_HTTP_PORT     = 9904;                                     //8080;
   public static final int                    DEFAULT_HTTPS_PORT    = 9906;                                     //8081;

   private final List<Server>                 servers               = new ArrayList<>();
   private final LinkedList<Integer>          clientSessionsCounter = new LinkedList<>();
   private final WebRoutes                    webRoutes             = new WebRoutes();
   private final Map<Integer, WebHttpSession> clients               = new ConcurrentHashMap<>();
   private final WebRootInstaller             webInstaller          = new WebRootInstaller();
   private final Map<Long, ServiceTopic>      topics                = new ConcurrentHashMap<>();

   protected static final AtomicInteger       clientCounter         = new AtomicInteger();
   private long                               lastStatsLog;

   public WebService() throws IOException {
      super(new WebContract());
      addContracts(new TetrapodContract());

      addSubscriptionHandler(new TetrapodContract.Pubsub(), this);
      addSubscriptionHandler(new TetrapodContract.Services(), this);

      logger.info(" ***** WebService ***** ");

      // add the tetrapod admin web root
      webInstaller.install(new WebRootDef("tetrapod", "/", "webContent"));

      LongPollToken.setSecret(AuthToken.generateRandomBytes(64));
   }

   @Override
   public String getServiceIcon() {
      return "fa-cloud";
   }

   @Override
   public long getCounter() {
      return getNumActiveClients();
   }

   @Override
   public void onReadyToServe() {
      logger.info(" ***** READY TO SERVE ***** ");
      try {
         if (isStartingUp()) {
            servers.add(new Server(Util.getProperty("tetrapod.http.port", DEFAULT_HTTP_PORT), (ch) -> makeWebSession(ch, Util.isProduction()),
                  dispatcher));
            // create secure port servers, if configured
            if (sslContext != null) {
               servers.add(new Server(Util.getProperty("tetrapod.https.port", DEFAULT_HTTPS_PORT), (ch) -> makeWebSession(ch, true),
                     dispatcher, sslContext, false));
            }
            scheduleHealthCheck();

            // start listening
            for (Server s : servers) {
               s.start().sync();
            }
         }
      } catch (Exception e) {
         fail(e);
      }
   }

   @Override
   protected void onConnectedToCluster() {
      super.onConnectedToCluster();
      clusterClient.getSession().setRelayHandler(this);
      if (!isStartingUp()) {
         try {
            for (Server httpServer : servers) {
               httpServer.start().sync();
            }
         } catch (Exception e) {
            fail(e);
         }
      }
   }

   @Override
   public void onDisconnectedFromCluster() {
      // drop our client connections if we lose connection to cluster
      for (Server httpServer : servers) {
         httpServer.close();
         httpServer.purge();
      }
   }

   // Pause will close the HTTP and HTTPS ports on the web service
   @Override
   public void onPaused() {
      for (Server httpServer : servers) {
         httpServer.close();
         httpServer.purge();
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

   @Override
   public ServiceCommand[] getServiceCommands() {
      return new ServiceCommand[] { new ServiceCommand("Close Client Connection", null, CloseClientConnectionRequest.CONTRACT_ID,
            CloseClientConnectionRequest.STRUCT_ID, true), };
   }

   public Session makeWebSession(SocketChannel ch, boolean allowWebSockets) {
      final WebHttpSession ses = new WebHttpSession(ch, this, webInstaller.getWebRoots(), allowWebSockets ? "/sockets" : null);
      ses.setRelayHandler(this);
      ses.setMyEntityId(getEntityId());
      ses.setMyEntityType(Core.TYPE_SERVICE);
      ses.setTheirEntityType(Core.TYPE_CLIENT);
      ses.addSessionListener(new Session.Listener() {
         @Override
         public void onSessionStop(Session ses) {
            logger.debug("Web Session Stopped: {}", ses);
            if (ses.getTheirEntityId() != 0) {
               clients.remove(ses.getTheirEntityId());
               if (LongPollQueue.getQueue(ses.getTheirEntityId(), false) == null) {
                  clearAllSubscriptions(ses.getTheirEntityId());
               }
            }
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
      if (entityId != parentId) {
         Entity entity = null;
         if (entityId == Core.UNADDRESSED) {
            entity = services.getRandomAvailableService(contractId);
         } else {
            entity = services.getEntity(entityId);
         }
         if (entity != null) {
            if (serviceConnector != null) {
               DirectServiceInfo info = serviceConnector.getDirectServiceInfo(entity.entityId);
               if (info.getSession() == null || !info.ses.isConnected()) {
                  info.considerConnecting();
               }
               Session ses = info.getSession();
               if (ses != null && ses.isConnected()) {
                  return ses;
               }
            }
         }
      }
      // route to parent tetrapod
      return clusterClient.getSession();
   }

   @Override
   public void relayMessage(MessageHeader header, ByteBuf buf, boolean isBroadcast) throws IOException {
      //logger.info("*** relayMessage {} isBroadcast={} {}/{}/{}", header.toChildId, isBroadcast, buf.readerIndex(), buf.readableBytes(), buf.capacity());

      final int ri = buf.readerIndex();
      if (isBroadcast) {
         if ((header.flags & MessageHeader.FLAGS_ALTERNATE) != 0) {
            for (WebHttpSession ses : clients.values()) {
               if (ses.getAlternateId() == header.toChildId) {
                  ses.sendRelayedMessage(header, buf, isBroadcast);
                  buf.readerIndex(ri);
               }
            }
         } else {
            final ServiceTopic topic = topics.get(topicKey(header.fromId, header.topicId));
            if (topic != null) {
               synchronized (topic) {
                  for (final Subscriber s : topic.getSubscribers()) {
                     if (s.entityId == 0) {
                        // that's us, dispatch to self
                        ByteBufDataSource reader = new ByteBufDataSource(buf);
                        final Object obj = StructureFactory.make(header.contractId, header.structId);
                        final Message msg = (obj instanceof Message) ? (Message) obj : null;
                        if (msg != null) {
                           msg.read(reader);
                           clusterClient.getSession().dispatchMessage(header, msg);
                        } else {
                           logger.warn("Could not read message for self-dispatch {}", header.dump());
                        }
                        buf.readerIndex(ri);
                     } else {
                        WebHttpSession ses = clients.get(s.entityId);
                        if (ses != null) {
                           ses.sendRelayedMessage(header, buf, isBroadcast);
                           buf.readerIndex(ri);
                        }
                     }
                  }
               }
            } else {
               logger.warn("Could not find topic {} for entity {} : {}", header.topicId, header.fromId, header.dump());
               sendMessage(new TopicNotFoundMessage(header.fromId, header.topicId), header.fromId, 0);
            }
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

      // for all of our clients:
      for (final WebSession ses : clients.values()) {
         // special check for long-polling clients
         if (now - ses.getLastHeardFrom() > Util.ONE_MINUTE) {
            ses.close();
         }
      }

      for (int childId : LongPollQueue.removeExpired(System.currentTimeMillis() - Util.ONE_MINUTE)) {
         // TODO: cleanup
         clearAllSubscriptions(childId);
      }

   }

   private int getNumActiveClients() {
      return servers.stream().mapToInt(Server::getNumSessions).sum();
   }

   @Override
   public Response requestRegister(RegisterRequest r, RequestContext ctxA) {
      final SessionRequestContext ctx = (SessionRequestContext) ctxA;
      final int entityId = clientCounter.incrementAndGet();
      WebHttpSession ses = (WebHttpSession) ctx.session;
      ses.setTheirEntityId(entityId);
      ses.setBuild(r.build);
      ses.setName(r.name);
      ses.setHttpReferrer(r.referrer);
      clients.put(entityId, ses);
      return new RegisterResponse(entityId, getEntityId(), LongPollToken.encodeToken(entityId, 10));
   }

   @Override
   public Response requestKeepAlive(KeepAliveRequest r, RequestContext ctx) {
      return Response.SUCCESS;
   }

   //////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public void genericMessage(Message message, MessageContext ctx) {}

   public long topicKey(int publisherId, int topicId) {
      return ((long) (publisherId) << 32) | topicId;
   }

   @Override
   public void messageServiceAdded(ServiceAddedMessage m, MessageContext ctx) {
      if (serviceConnector != null && (m.entity.status & (Core.STATUS_GONE | Core.STATUS_STARTING)) == 0) {
         DirectServiceInfo info = serviceConnector.getDirectServiceInfo(m.entity.entityId);
         if (info.getSession() == null || !info.ses.isConnected()) {
            info.considerConnecting();
         }
      }
   }

   @Override
   public void messageServiceRemoved(ServiceRemovedMessage m, MessageContext ctx) {}

   @Override
   public void messageServiceUpdated(ServiceUpdatedMessage m, MessageContext ctx) {
      if (serviceConnector != null && (m.status & (Core.STATUS_GONE | Core.STATUS_STARTING)) == 0) {
         DirectServiceInfo info = serviceConnector.getDirectServiceInfo(m.entityId);
         if (info.getSession() == null || !info.ses.isConnected()) {
            info.considerConnecting();
         }
      }
   }

   @Override
   public void messageTopicPublished(final TopicPublishedMessage m, MessageContext ctx) {
      logger.debug("******* {} {}", ctx.header.dump(), m.dump());
      final ServiceTopic topic = topics.get(topicKey(m.publisherId, m.topicId));
      if (topic == null) {
         topics.put(topicKey(m.publisherId, m.topicId), new ServiceTopic(m.publisherId, m.topicId));
      } else {
         logger.warn("Topic {} already exists?", topic);
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
                     // notify the subscriber that they have been unsubscribed from this topic
                     s.sendMessage(new TopicUnsubscribedMessage(m.publisherId, topic.topicId, entityId, sub.entityId), entityId,
                           sub.entityId);
                  }
               }
            }
         }
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
               s.sendMessage(new TopicUnsubscribedMessage(m.publisherId, topic.topicId, entityId, m.childId), entityId, m.childId);
            }
         }
      } else {
         logger.info("Could not find publisher entity {}", ctx.header.fromId);
      }
   }

   @Override
   public void messageWebRootAdded(WebRootAddedMessage m, MessageContext ctx) {
      webInstaller.install(m.def);
   }

   @Override
   public void messageWebRootRemoved(WebRootRemovedMessage m, MessageContext ctx) {
      webInstaller.uninstall(m.name);
   }

   @Override
   public void messageRegisterContract(RegisterContractMessage m, MessageContext ctx) {
      // reg the structs
      if (m.info.structs != null) {
         for (StructDescription sd : m.info.structs) {
            if (m.info.contractId != WebContract.CONTRACT_ID) {
               StructureFactory.add(new StructureAdapter(sd));
            }
         }
      }
      // reg the web routes
      if (m.info.routes != null) {
         for (WebRoute r : m.info.routes) {
            webRoutes.setRoute(r.path, r.contractId, r.subContractId, r.structId);
            logger.debug("Setting Web route [{}] for {} {}", r.path, r.contractId, r.subContractId);
         }
      }
      webRoutes.clear(m.info.contractId, m.info.subContractId, m.info.routes);
   }

   //////////////////////////////////////////////////////////////////////////////////////////

   @Override
   public Response requestSetAlternateId(SetAlternateIdRequest r, RequestContext ctx) {
      final WebSession s = clients.get(r.clientId);
      if (s != null) {
         s.setAlternateId(r.alternateId);
         return Response.SUCCESS;
      }
      return Response.error(ERROR_INVALID_ENTITY);
   }

   @Override
   public Response requestGetClientInfo(GetClientInfoRequest r, RequestContext ctx) {
      final WebHttpSession s = clients.get(r.clientId);
      if (s != null) {
         return new GetClientInfoResponse(s.getBuild(), s.getName(), s.getPeerHostname(), s.getHttpReferrer(), s.getDomain());
      } else {
         return Response.error(WebContract.ERROR_UNKNOWN_CLIENT_ID);
      }
   }

   @Override
   public Response requestClientSessions(ClientSessionsRequest r, RequestContext ctx) {
      synchronized (clientSessionsCounter) {
         return new ClientSessionsResponse(Util.toIntArray(clientSessionsCounter));
      }
   }

   @Override
   public Response requestCloseClientConnection(CloseClientConnectionRequest r, RequestContext ctx) {
      int accountId = Integer.parseInt(r.data);
      for (WebHttpSession ses : clients.values()) {
         if (ses.getAlternateId() == accountId) {
            ses.close();
         }
      }
      return Response.error(WebContract.ERROR_UNKNOWN_ALT_ID);
   }

   private void clearAllSubscriptions(final int childId) {
      logger.debug("clearAllSubscriptions: {}", childId);
      // Unsubscribe from all subscriptions we're managing
      for (ServiceTopic topic : topics.values()) {
         if (topic.unsubscribe(childId, true)) {
            // notify the publisher that this client's subscription is now dead
            sendMessage(new TopicUnsubscribedMessage(topic.ownerId, topic.topicId, entityId, childId), topic.ownerId, 0);
         }
      }
   }

}
