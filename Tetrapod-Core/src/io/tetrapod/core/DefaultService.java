package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.UNADDRESSED;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.utils.Fail;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ConnectException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;

public class DefaultService implements Service, Fail.FailHandler, CoreContract.API, SessionFactory, EntityMessage.Handler,
      ClusterMemberMessage.Handler {

   private static final Logger             logger          = LoggerFactory.getLogger(DefaultService.class);

   protected final EventLoopGroup          bossGroup       = new NioEventLoopGroup();

   protected final Set<Integer>            dependencies    = new HashSet<>();

   protected final Dispatcher              dispatcher;
   protected final Client                  clusterClient;
   protected Contract                      contract;
   protected final ServiceCache            services;
   protected boolean                       terminated;
   protected int                           entityId;
   protected int                           parentId;
   protected String                        token;
   protected int                           status;
   protected final int                     buildNumber;
   protected final LogBuffer               logBuffer;
   private ServiceConnector                serviceConnector;

   protected final ServiceStats            stats;

   private final LinkedList<ServerAddress> clusterMembers  = new LinkedList<ServerAddress>();

   private final MessageHandlers           messageHandlers = new MessageHandlers();

   public DefaultService() {
      logBuffer = (LogBuffer) ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("ROOT").getAppender("BUFFER");
      String m = getStartLoggingMessage();
      logger.info(m);
      Session.commsLog.info(m);
      Fail.handler = this;
      Metrics.init(getMetricsPrefix());
      status |= Core.STATUS_STARTING;
      dispatcher = new Dispatcher();
      clusterClient = new Client(this);
      stats = new ServiceStats(this);
      addContracts(new CoreContract());
      addPeerContracts(new TetrapodContract());
      addMessageHandler(new EntityMessage(), this);
      addMessageHandler(new ClusterMemberMessage(), this);

      if (getEntityType() != Core.TYPE_TETRAPOD) {
         services = new ServiceCache();
         addSubscriptionHandler(new TetrapodContract.Services(), services);
      } else {
         services = null;
      }

      Runtime.getRuntime().addShutdownHook(new Thread("Shutdown Hook") {
         public void run() {
            logger.info("Shutdown Hook");
            if (!isShuttingDown()) {
               shutdown(false);
            }
         }
      });

      int num = 1;
      try {
         String b = Util.readFileAsString(new File("build_number.txt"));
         num = Integer.parseInt(b.trim());
      } catch (IOException e) {}
      buildNumber = num;

      checkHealth();
   }

   /**
    * Returns a prefix for all exported metrics from this service.
    */
   private String getMetricsPrefix() {
      return Util.getProperty("devMode", "") + "." + Util.getProperty("product.name") + "." + Util.getHostName() + "."
            + getClass().getSimpleName();
   }

   public byte getEntityType() {
      return Core.TYPE_SERVICE;
   }

   public synchronized int getStatus() {
      return status;
   }

   @Override
   public void messageEntity(EntityMessage m, MessageContext ctxA) {
      SessionMessageContext ctx = (SessionMessageContext) ctxA;
      if (ctx.session.getTheirEntityType() == Core.TYPE_TETRAPOD) {
         synchronized (this) {
            this.entityId = m.entityId;
         }
         ctx.session.setMyEntityId(m.entityId);
      }
   }

   @Override
   public void genericMessage(Message message, MessageContext ctx) {
      logger.error("Unhandled message handler: {}", message.dump());
      assert false;
   }

   // Service protocol

   @Override
   public void startNetwork(ServerAddress server, String token, Map<String, String> otherOpts) throws Exception {
      this.token = token;
      clusterMembers.addFirst(server);
      connectToCluster(5);
   }

   /**
    * Called after registration is complete and this service has a valid entityId and is free to make requests into the cluster. Default
    * implementation is to do nothing.
    */
   public void onRegistered() {}

   /**
    * Called after we've registered and dependencies are all available
    */
   public void onReadyToServe() {}

   private void onServiceRegistered() {
      registerServiceInformation();
      stats.publishTopic();
      sendDirectRequest(new ServicesSubscribeRequest());
      onRegistered();
      checkDependencies();
   }

   private void checkDependencies() {
      if (!isShuttingDown()) {
         if (getEntityType() == Core.TYPE_TETRAPOD || services.checkDependencies(dependencies)) {
            try {
               onReadyToServe();
               if (getEntityType() != Core.TYPE_TETRAPOD) {
                  if (serviceConnector != null) {
                     serviceConnector.shutdown();
                  }
                  serviceConnector = new ServiceConnector(this);

               }
            } catch (Throwable t) {
               fail(t);
            }
            // ok, we're good to go
            updateStatus(status & ~Core.STATUS_STARTING);
            setWebRoot();
         } else {
            dispatcher.dispatch(1, TimeUnit.SECONDS, new Runnable() {
               public void run() {
                  checkDependencies();
               }
            });
         }
      }
   }

   /**
    * Periodically checks service health, updates metrics
    */
   private void checkHealth() {
      if (!isShuttingDown()) {
         if (dispatcher.workQueueSize.getCount() > 0) {
            logger.warn("DISPATCHER QUEUE SIZE = {}", dispatcher.workQueueSize.getCount());
         }
         if (dispatcher.workQueueSize.getCount() >= Session.DEFAULT_OVERLOAD_THRESHOLD) {
            updateStatus(status | Core.STATUS_OVERLOADED);
         } else {
            updateStatus(status & ~Core.STATUS_OVERLOADED);
         }

         dispatcher.dispatch(1, TimeUnit.SECONDS, new Runnable() {
            public void run() {
               checkHealth();
            }
         });
      }
   }

   /**
    * Called before shutting down. Default implementation is to do nothing. Subclasses are expecting to close any resources they opened (for
    * example database connections or file handles).
    * 
    * @param restarting true if we are shutting down in order to restart
    */
   public void onShutdown(boolean restarting) {}

   public void onPaused() {}

   public void onUnpaused() {}

   public void shutdown(boolean restarting) {
      updateStatus(status | Core.STATUS_STOPPING);
      try {
         onShutdown(restarting);
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
      }

      try {
         // we have one boss group for all our servers
         bossGroup.shutdownGracefully().sync();
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
      }

      if (restarting) {
         clusterClient.close();
         dispatcher.shutdown();
         setTerminated(true);
         try {
            Launcher.relaunch(getRelaunchToken());
         } catch (Exception e) {
            logger.error(e.getMessage(), e);
         }
      } else {
         sendDirectRequest(new UnregisterRequest(getEntityId())).handle(new ResponseHandler() {
            @Override
            public void onResponse(Response res) {
               clusterClient.close();
               dispatcher.shutdown();
               setTerminated(true);
            }
         });
      }
   }

   protected String getRelaunchToken() {
      return token;
   }

   /**
    * Session factory for our session to our parent TetrapodService
    */
   @Override
   public Session makeSession(SocketChannel ch) {
      final Session ses = new WireSession(ch, DefaultService.this);
      ses.setMyEntityType(getEntityType());
      ses.setTheirEntityType(Core.TYPE_TETRAPOD);
      ses.addSessionListener(new Session.Listener() {
         @Override
         public void onSessionStop(Session ses) {
            onDisconnectedFromCluster();
         }

         @Override
         public void onSessionStart(Session ses) {
            onConnectedToCluster();
         }
      });
      return ses;
   }

   public void onConnectedToCluster() {
      sendDirectRequest(new RegisterRequest(buildNumber, token, getContractId(), getShortName(), status, Util.getHostName())).handle(
            new ResponseHandler() {
               @Override
               public void onResponse(Response res) {
                  if (res.isError()) {
                     Fail.fail("Unable to register: " + res.errorCode());
                  } else {
                     RegisterResponse r = (RegisterResponse) res;
                     entityId = r.entityId;
                     parentId = r.parentId;
                     token = r.token;

                     logger.info(String.format("%s My entityId is 0x%08X", clusterClient.getSession(), r.entityId));
                     clusterClient.getSession().setMyEntityId(r.entityId);
                     clusterClient.getSession().setTheirEntityId(r.parentId);
                     clusterClient.getSession().setMyEntityType(getEntityType());
                     clusterClient.getSession().setTheirEntityType(Core.TYPE_TETRAPOD);
                     onServiceRegistered();
                  }
               }
            });
   }

   public void onDisconnectedFromCluster() {
      if (!isShuttingDown()) {
         logger.info("Connection to tetrapod closed");
         dispatcher.dispatch(3, TimeUnit.SECONDS, new Runnable() {
            public void run() {
               connectToCluster(1);
            }
         });
      }
   }

   protected void connectToCluster(final int retrySeconds) {
      if (!isShuttingDown() && !clusterClient.isConnected()) {
         synchronized (clusterMembers) {
            final ServerAddress server = clusterMembers.poll();
            if (server != null) {
               try {
                  clusterClient.connect(server.host, server.port, dispatcher).sync();
                  if (clusterClient.isConnected()) {
                     clusterMembers.addFirst(server);
                     return;
                  }
               } catch (ConnectException e) {
                  logger.info(e.getMessage());
               } catch (Throwable e) {
                  logger.error(e.getMessage(), e);
               }
               clusterMembers.addLast(server);
            }
         }

         // schedule a retry
         dispatcher.dispatch(retrySeconds, TimeUnit.SECONDS, new Runnable() {
            public void run() {
               connectToCluster(retrySeconds);
            }
         });
      }
   }

   // subclass utils

   protected void setMainContract(Contract c) {
      addContracts(c);
      contract = c;
   }

   protected void addContracts(Contract... contracts) {
      for (Contract c : contracts) {
         c.registerStructs();
      }
   }

   protected void addPeerContracts(Contract... contracts) {
      for (Contract c : contracts) {
         c.registerPeerStructs();
      }
   }

   public int getEntityId() {
      return entityId;
   }

   protected int getParentId() {
      return parentId;
   }

   public synchronized boolean isShuttingDown() {
      return (status & Core.STATUS_STOPPING) != 0;
   }

   public synchronized boolean isPaused() {
      return (status & Core.STATUS_PAUSED) != 0;
   }

   public synchronized boolean isStartingUp() {
      return (status & Core.STATUS_STARTING) != 0;
   }

   public synchronized boolean isNominal() {
      int nonRunning = Core.STATUS_STARTING | Core.STATUS_FAILED | Core.STATUS_BUSY | Core.STATUS_PAUSED | Core.STATUS_STOPPING;
      return (status & nonRunning) == 0;
   }

   public synchronized boolean isTerminated() {
      return terminated;
   }

   private synchronized void setTerminated(boolean val) {
      logger.info("TERMINATED");
      terminated = val;
   }

   protected void updateStatus(int status) {
      boolean changed = false;
      synchronized (this) {
         changed = this.status != status;
         this.status = status;
      }
      if (changed && clusterClient.isConnected()) {
         sendDirectRequest(new ServiceStatusUpdateRequest(status));
      }
   }

   @Override
   public void fail(Throwable error) {
      logger.error(error.getMessage(), error);
      updateStatus(status | Core.STATUS_FAILED);
   }

   @Override
   public void fail(String reason) {
      logger.error("FAIL: {}", reason);
      updateStatus(status | Core.STATUS_FAILED);
   }

   /**
    * Get a URL for this service's icon to display in the admin apps. Subclasses should override this to customize
    */
   public String getServiceIcon() {
      return "media/gear.gif";
   }

   /**
    * Get any custom metadata for the service. Subclasses should override this to customize
    */
   public String getServiceMetadata() {
      return null;
   }

   /**
    * Get any custom admin commands for the service to show in command menu of admin app. Subclasses should override this to customize
    */
   public ServiceCommand[] getServiceCommands() {
      return null;
   }

   protected String getShortName() {
      if (contract == null) {
         return null;
      }
      return contract.getName();
   }

   protected String getFullName() {
      if (contract == null) {
         return null;
      }
      String s = contract.getClass().getCanonicalName();
      return s.substring(0, s.length() - "Contract".length());
   }

   public String getHostName() {
      return Util.getHostName();
   }

   public long getAverageResponseTime() {
      // TODO: verify this is correctly converting nanos to millis
      return (long) dispatcher.requestTimes.getSnapshot().getMean() / 1000000L;
   }

   /**
    * Services can override this to provide a service specific counter for display in the admin app
    */
   public long getCounter() {
      return 0;
   }

   public long getNumRequestsHandled() {
      return dispatcher.requestsHandledCounter.getCount();
   }

   public long getNumMessagesSent() {
      return dispatcher.messagesSentCounter.getCount();
   }

   public Response sendPendingRequest(Request req, int toEntityId, PendingResponseHandler handler) {
      if (serviceConnector != null) {
         return serviceConnector.sendPendingRequest(req, toEntityId, handler);
      }
      return clusterClient.getSession().sendPendingRequest(req, toEntityId, (byte) 30, handler);
   }

   public Response sendPendingRequest(Request req, PendingResponseHandler handler) {
      if (serviceConnector != null) {
         return serviceConnector.sendPendingRequest(req, Core.UNADDRESSED, handler);
      }
      return clusterClient.getSession().sendPendingRequest(req, Core.UNADDRESSED, (byte) 30, handler);
   }

   public Response sendPendingDirectRequest(Request req, PendingResponseHandler handler) {
      return clusterClient.getSession().sendPendingRequest(req, Core.DIRECT, (byte) 30, handler);
   }

   public Async sendRequest(Request req) {
      if (serviceConnector != null) {
         return serviceConnector.sendRequest(req, Core.UNADDRESSED);
      }
      return clusterClient.getSession().sendRequest(req, Core.UNADDRESSED, (byte) 30);
   }

   public Async sendRequest(Request req, int toEntityId) {
      if (serviceConnector != null) {
         return serviceConnector.sendRequest(req, toEntityId);
      }
      return clusterClient.getSession().sendRequest(req, toEntityId, (byte) 30);
   }

   public Async sendDirectRequest(Request req) {
      return clusterClient.getSession().sendRequest(req, Core.DIRECT, (byte) 30);
   }

   public void sendMessage(Message msg, int toEntityId) {
      clusterClient.getSession().sendMessage(msg, MessageHeader.TO_ENTITY, toEntityId);
   }

   public void sendBroadcastMessage(Message msg, int topicId) {
      clusterClient.getSession().sendBroadcastMessage(msg, MessageHeader.TO_TOPIC, topicId);
   }

   public void sendAltBroadcastMessage(Message msg, int altId) {
      clusterClient.getSession().sendBroadcastMessage(msg, MessageHeader.TO_ALTERNATE, altId);
   }

   public void subscribe(int topicId, int entityId) {
      sendMessage(new TopicSubscribedMessage(getEntityId(), topicId, entityId), UNADDRESSED);
   }

   public void unsubscribe(int topicId, int entityId) {
      sendMessage(new TopicUnsubscribedMessage(getEntityId(), topicId, entityId), UNADDRESSED);
   }

   public void unpublish(int topicId) {
      sendMessage(new TopicUnpublishedMessage(getEntityId(), topicId), UNADDRESSED);
   }

   // Generic handlers for all request/subscriptions

   public Response genericRequest(Request r, RequestContext ctx) {
      logger.error("unhandled request " + r.dump());
      return new Error(CoreContract.ERROR_UNKNOWN_REQUEST);
   }

   public void setDependencies(int... contractIds) {
      for (int contractId : contractIds) {
         dependencies.add(contractId);
      }
   }

   // Session.Help implementation

   @Override
   public Dispatcher getDispatcher() {
      return dispatcher;
   }

   @Override
   public ServiceAPI getServiceHandler(int contractId) {
      // this method allows us to have delegate objects that directly handle some contracts
      return this;
   }

   @Override
   public List<SubscriptionAPI> getMessageHandlers(int contractId, int structId) {
      return messageHandlers.get(contractId, structId);
   }

   @Override
   public int getContractId() {
      return contract == null ? 0 : contract.getContractId();
   }

   public void addSubscriptionHandler(Contract sub, SubscriptionAPI handler) {
      messageHandlers.add(sub, handler);
   }

   public void addMessageHandler(Message k, SubscriptionAPI handler) {
      messageHandlers.add(k, handler);
   }

   @Override
   public void messageClusterMember(ClusterMemberMessage m, MessageContext ctx) {
      clusterMembers.add(new ServerAddress(m.host, m.servicePort));
   }

   // private methods

   protected void registerServiceInformation() {
      if (contract != null) {
         AddServiceInformationRequest asi = new AddServiceInformationRequest();
         asi.routes = contract.getWebRoutes();
         asi.structs = new ArrayList<>();
         for (Structure s : contract.getRequests()) {
            asi.structs.add(s.makeDescription());
         }
         for (Structure s : contract.getResponses()) {
            asi.structs.add(s.makeDescription());
         }
         for (Structure s : contract.getMessages()) {
            asi.structs.add(s.makeDescription());
         }
         for (Structure s : contract.getStructs()) {
            asi.structs.add(s.makeDescription());
         }
         sendDirectRequest(asi).handle(ResponseHandler.LOGGER);
      }
   }

   // Base service implementation

   @Override
   public Response requestPause(PauseRequest r, RequestContext ctx) {
      updateStatus(status | Core.STATUS_PAUSED);
      onPaused();
      return Response.SUCCESS;
   }

   @Override
   public Response requestUnpause(UnpauseRequest r, RequestContext ctx) {
      updateStatus(status & ~Core.STATUS_PAUSED);
      onUnpaused();
      return Response.SUCCESS;
   }

   @Override
   public Response requestRestart(RestartRequest r, RequestContext ctx) {
      dispatcher.dispatch(new Runnable() {
         public void run() {
            shutdown(true);
         }
      });
      return Response.SUCCESS;
   }

   @Override
   public Response requestShutdown(ShutdownRequest r, RequestContext ctx) {
      dispatcher.dispatch(new Runnable() {
         public void run() {
            shutdown(false);
         }
      });
      return Response.SUCCESS;
   }

   @Override
   public Response requestServiceDetails(ServiceDetailsRequest r, RequestContext ctx) {
      return new ServiceDetailsResponse(getServiceIcon(), getServiceMetadata(), getServiceCommands());
   }

   @Override
   public Response requestServiceStatsSubscribe(ServiceStatsSubscribeRequest r, RequestContext ctx) {
      stats.subscribe(ctx.header.fromId);
      return Response.SUCCESS;
   }

   @Override
   public Response requestServiceStatsUnsubscribe(ServiceStatsUnsubscribeRequest r, RequestContext ctx) {
      stats.unsubscribe(ctx.header.fromId);
      return Response.SUCCESS;
   }

   private void setWebRoot() {
      String name = Launcher.getOpt("webOnly");
      if (name == null) {
         name = getShortName();
      }
      try {
         recursiveAddWebFiles(name, new File("webContent"), true);
         if (Util.isLocal()) {
            for (File f : getDevProtocolWebRoots())
               recursiveAddWebFiles(name, f, false);
         }
         if (Launcher.getOpt("webOnly") != null) {
            shutdown(false);
         }
      } catch (IOException e) {
         logger.error("bad web root path", e);
      }
   }

   private static final Set<String> VALID_EXTENSIONS = new HashSet<>(Arrays.asList(new String[] { "html", "htm", "js", "css", "jpg", "png",
         "gif", "wav", "woff", "svg", "ttf", "swf"  }));

   private void recursiveAddWebFiles(String webRootName, File dir, boolean first) throws IOException {
      if (!dir.exists())
         return;
      if (Util.isLocal()) {
         sendDirectRequest(new AddWebFileRequest(dir.getCanonicalPath(), webRootName, null, first));
         return;
      }
      ArrayList<File> files = new ArrayList<>(Arrays.asList(dir.listFiles()));
      while (!files.isEmpty()) {
         File f = files.remove(0);
         if (f.isDirectory()) {
            files.addAll(Arrays.asList(f.listFiles()));
            continue;
         }
         int ix = f.getName().lastIndexOf(".");
         String ext = ix < 0 ? "" : f.getName().substring(ix + 1).toLowerCase();
         if (VALID_EXTENSIONS.contains(ext)) {
            byte[] contents = Files.readAllBytes(f.toPath());
            String path = "/" + dir.toPath().relativize(f.toPath()).toString();
            Async a = sendDirectRequest(new AddWebFileRequest(path, webRootName, contents, first));
            if (first) {
               // have to wait for the first one to finish so the first flag is really 
               // the first one that is processed
               a.waitForResponse();
            }
            first = false;
         }
      }
   }

   protected File[] getDevProtocolWebRoots() {
      if (getShortName() == null) {
         return new File[] {};
      }
      return new File[] { new File("../Protocol-" + getShortName() + "/rsc") };
   }

   @Override
   public Response requestServiceLogs(ServiceLogsRequest r, RequestContext ctx) {
      if (logBuffer == null) {
         return new Error(CoreContract.ERROR_NOT_CONFIGURED);
      }
      final List<ServiceLogEntry> list = new ArrayList<ServiceLogEntry>();
      long last = logBuffer.getItems(r.logId, logBuffer.convert(r.level), r.maxItems, list);
      return new ServiceLogsResponse(last, list);
   }

   protected String getStartLoggingMessage() {
      return "*** Start Service ***" + "\n   *** Service name: " + Util.getProperty("APPNAME") + "\n   *** Options: "
            + Launcher.getAllOpts() + "\n   *** VM Args: " + ManagementFactory.getRuntimeMXBean().getInputArguments().toString();
   }

   @Override
   public Response requestWebAPI(WebAPIRequest r, RequestContext ctx) {
      return Response.error(CoreContract.ERROR_UNKNOWN_REQUEST);
   }

   @Override
   public Response requestDirectConnection(DirectConnectionRequest r, RequestContext ctx) {
      if (serviceConnector != null) {
         return serviceConnector.requestDirectConnection(r, ctx);
      }
      return new Error(CoreContract.ERROR_NOT_CONFIGURED);
   }

   @Override
   public Response requestValidateConnection(ValidateConnectionRequest r, RequestContext ctx) {
      if (serviceConnector != null) {
         return serviceConnector.requestValidateConnection(r, ctx);
      }
      return new Error(CoreContract.ERROR_NOT_CONFIGURED);
   }

   @Override
   public Response requestDummy(DummyRequest r, RequestContext ctx) {
      return Response.SUCCESS;
   }

}
