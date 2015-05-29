package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.UNADDRESSED;
import static io.tetrapod.protocol.core.CoreContract.*;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.utils.*;
import io.tetrapod.protocol.core.*;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.ConnectException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.slf4j.*;

import ch.qos.logback.classic.LoggerContext;

import com.codahale.metrics.Timer.Context;

public class DefaultService implements Service, Fail.FailHandler, CoreContract.API, SessionFactory, EntityMessage.Handler,
      TetrapodContract.Cluster.API {

   private static final Logger             logger          = LoggerFactory.getLogger(DefaultService.class);

   protected final Set<Integer>            dependencies    = new HashSet<>();

   public final Dispatcher                 dispatcher;
   protected final Client                  clusterClient;
   protected final Contract                contract;
   protected final ServiceCache            services;
   protected boolean                       terminated;
   protected int                           entityId;
   protected int                           parentId;
   protected String                        token;
   private int                             status;
   public final int                        buildNumber;
   protected final LogBuffer               logBuffer;
   protected SSLContext                    sslContext;

   private ServiceConnector                serviceConnector;

   protected final ServiceStats            stats;
   protected boolean                       startPaused;

   private final LinkedList<ServerAddress> clusterMembers  = new LinkedList<>();

   private final MessageHandlers           messageHandlers = new MessageHandlers();

   public DefaultService() {
      this(null);
   }

   public DefaultService(Contract mainContract) {
      logBuffer = (LogBuffer) ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("ROOT").getAppender("BUFFER");
      String m = getStartLoggingMessage();
      logger.info(m);
      Session.commsLog.info(m);
      Fail.handler = this;
      Metrics.init(getMetricsPrefix());
      synchronized (this) {
         status |= Core.STATUS_STARTING;
      }
      dispatcher = new Dispatcher();
      clusterClient = new Client(this);
      stats = new ServiceStats(this);
      addContracts(new CoreContract());
      addPeerContracts(new TetrapodContract());
      addMessageHandler(new EntityMessage(), this);
      addSubscriptionHandler(new TetrapodContract.Cluster(), this);

      try {
         if (Util.getProperty("tetrapod.tls", true)) {
            sslContext = Util.createSSLContext(new FileInputStream(Util.getProperty("tetrapod.jks.file", "cfg/tetrapod.jks")), System
                  .getProperty("tetrapod.jks.pwd", "4pod.dop4").toCharArray());
         }
      } catch (Exception e) {
         fail(e);
      }

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
      if (mainContract != null)
         addContracts(mainContract);
      this.contract = mainContract;
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
      this.startPaused = otherOpts.get("paused").equals("true");
      clusterMembers.addFirst(server);
      connectToCluster(5);
   }

   /**
    * Called after registration is complete and this service has a valid entityId and is free to make requests into the cluster. Default
    * implementation is to do nothing.
    */
   //public void onRegistered() {}

   /**
    * Called after we've registered and dependencies are all available
    */
   public void onReadyToServe() {}

   private void onServiceRegistered() {
      registerServiceInformation();
      stats.publishTopic();
      sendDirectRequest(new ServicesSubscribeRequest());
   }

   
   public boolean dependenciesReady() {
      return services.checkDependencies(dependencies);
   }
   
   public void checkDependencies() {
      if (!isShuttingDown()) {
         if (dependenciesReady()) {
            try {
               if (startPaused) {
                  updateStatus(getStatus() | Core.STATUS_PAUSED);
               }
               onReadyToServe();
               if (getEntityType() != Core.TYPE_TETRAPOD) {
                  if (serviceConnector != null) {
                     serviceConnector.shutdown();
                  }
                  serviceConnector = new ServiceConnector(this, sslContext);
               }
            } catch (Throwable t) {
               fail(t);
            }
            // ok, we're good to go
            updateStatus(getStatus() & ~Core.STATUS_STARTING);
            onStarted();
            if (startPaused) {
               onPaused();
               startPaused = false; // only start paused once
            }
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
            updateStatus(getStatus() | Core.STATUS_OVERLOADED);
         } else {
            updateStatus(getStatus() & ~Core.STATUS_OVERLOADED);
         }

         if (logBuffer.hasErrors()) {
            updateStatus(getStatus() | Core.STATUS_ERRORS);
         } else {
            updateStatus(getStatus() & ~Core.STATUS_ERRORS);
         }
         if (logBuffer.hasWarnings()) {
            updateStatus(getStatus() | Core.STATUS_WARNINGS);
         } else {
            updateStatus(getStatus() & ~Core.STATUS_WARNINGS);
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

   public void onPurged() {}

   public void onReleaseExcess() {}

   public void onRebalance() {}

   public void onUnpaused() {}

   public void onStarted() {}

   public void shutdown(boolean restarting) {
      updateStatus(getStatus() | Core.STATUS_STOPPING);
      try {
         onShutdown(restarting);
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

      // If JVM doesn't gracefully terminate after 1 minute, explicitly kill the process
      final Thread hitman = new Thread(new Runnable() {
         public void run() {
            Util.sleep(Util.ONE_MINUTE);
            logger.warn("Service did not complete graceful termination. Force Killing JVM.");
            final Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();
            for (Thread t : map.keySet()) {
               logger.warn("{}", t);
            }
            System.exit(1);
         }
      }, "Hitman");
      hitman.setDaemon(true);
      hitman.start();
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

   private void onConnectedToCluster() {
      sendDirectRequest(new RegisterRequest(buildNumber, token, getContractId(), getShortName(), getStatus(), Util.getHostName())).handle(
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
                  if (sslContext != null) {
                     clusterClient.enableTLS(sslContext);
                  }
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
         sendDirectRequest(new ServiceStatusUpdateRequest(status)).log();
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

   /**
    * Dispatches a request to ourselves
    */
   @Override
   public Async dispatchRequest(final RequestHeader header, final Request req, final Session fromSession) {
      final Async async = new Async(req, header, fromSession);
      final ServiceAPI svc = getServiceHandler(header.contractId);
      if (svc != null) {
         final long start = System.nanoTime();
         final Context context = dispatcher.requestTimes.time();
         if (!dispatcher.dispatch(new Runnable() {
            public void run() {
               try {
                  RequestContext ctx = new SessionRequestContext(header, fromSession);
                  Response res = req.securityCheck(ctx);
                  if (res == null) {
                     res = req.dispatch(svc, ctx);
                  }
                  if (res != null) {
                     async.setResponse(res);
                  } else {
                     async.setResponse(new Error(ERROR_UNKNOWN));
                  }
               } catch (ErrorResponseException e) {
                  async.setResponse(new Error(e.errorCode));
               } catch (Throwable e) {
                  logger.error(e.getMessage(), e);
                  async.setResponse(new Error(ERROR_UNKNOWN));
               }
               context.stop();

               final long elapsed = System.nanoTime() - start;
               dispatcher.requestsHandledCounter.mark();
               if (Util.nanosToMillis(elapsed) > 1000) {
                  logger.warn("Request took {} {} millis", req, Util.nanosToMillis(elapsed));
               }
            }
         }, Session.DEFAULT_OVERLOAD_THRESHOLD)) {
            async.setResponse(new Error(ERROR_SERVICE_OVERLOADED));
         }
      } else {
         logger.warn("{} No handler found for {}", this, header.dump());
         async.setResponse(new Error(ERROR_UNKNOWN_REQUEST));
      }
      return async;
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

   /**
    * Subscribe an entity to the given topic. If once is true, tetrapod won't subscribe them a second time
    */
   public void subscribe(int topicId, int entityId, boolean once) {
      sendMessage(new TopicSubscribedMessage(getEntityId(), topicId, entityId, once), UNADDRESSED);
   }

   public void subscribe(int topicId, int entityId) {
      subscribe(topicId, entityId, false);
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

   @Override
   public void messageClusterPropertyAdded(ClusterPropertyAddedMessage m, MessageContext ctx) {
      logger.info("******** {}", m.dump());
      System.setProperty(m.property.key, m.property.val);
   }

   @Override
   public void messageClusterPropertyRemoved(ClusterPropertyRemovedMessage m, MessageContext ctx) {
      logger.info("******** {}", m.dump());
      System.clearProperty(m.key);
   }

   @Override
   public void messageClusterSynced(ClusterSyncedMessage m, MessageContext ctx) {
      checkDependencies();
   }

   // private methods

   protected void registerServiceInformation() {
      if (contract != null) {
         AddServiceInformationRequest asi = new AddServiceInformationRequest();
         asi.info = new ContractDescription();
         asi.info.contractId = contract.getContractId();
         asi.info.version = contract.getContractVersion();
         asi.info.routes = contract.getWebRoutes();
         asi.info.structs = new ArrayList<>();
         for (Structure s : contract.getRequests()) {
            asi.info.structs.add(s.makeDescription());
         }
         for (Structure s : contract.getResponses()) {
            asi.info.structs.add(s.makeDescription());
         }
         for (Structure s : contract.getMessages()) {
            asi.info.structs.add(s.makeDescription());
         }
         for (Structure s : contract.getStructs()) {
            asi.info.structs.add(s.makeDescription());
         }
         sendDirectRequest(asi).handle(ResponseHandler.LOGGER);
      }
   }

   // Base service implementation

   @Override
   public Response requestPause(PauseRequest r, RequestContext ctx) {
      // TODO: Check admin rights or macaroon
      updateStatus(getStatus() | Core.STATUS_PAUSED);
      onPaused();
      return Response.SUCCESS;
   }

   @Override
   public Response requestPurge(PurgeRequest r, RequestContext ctx) {
      onPurged();
      return Response.SUCCESS;
   }

   @Override
   public Response requestRebalance(RebalanceRequest r, RequestContext ctx) {
      onRebalance();
      return Response.SUCCESS;
   }

   @Override
   public Response requestReleaseExcess(ReleaseExcessRequest r, RequestContext ctx) {
      onReleaseExcess();
      return Response.SUCCESS;
   }

   @Override
   public Response requestUnpause(UnpauseRequest r, RequestContext ctx) {
      // TODO: Check admin rights or macaroon
      updateStatus(getStatus() & ~Core.STATUS_PAUSED);
      onUnpaused();
      return Response.SUCCESS;
   }

   @Override
   public Response requestRestart(RestartRequest r, RequestContext ctx) {
      // TODO: Check admin rights or macaroon
      dispatcher.dispatch(new Runnable() {
         public void run() {
            shutdown(true);
         }
      });
      return Response.SUCCESS;
   }

   @Override
   public Response requestShutdown(ShutdownRequest r, RequestContext ctx) {
      // TODO: Check admin rights or macaroon
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
   public Response requestServiceErrorLogs(ServiceErrorLogsRequest r, RequestContext ctx) {
      if (logBuffer == null) {
         return new Error(CoreContract.ERROR_NOT_CONFIGURED);
      }

      final List<ServiceLogEntry> list = new ArrayList<ServiceLogEntry>();
      list.addAll(logBuffer.getErrors());
      list.addAll(logBuffer.getWarnings());
      Collections.sort(list, new Comparator<ServiceLogEntry>() {
         @Override
         public int compare(ServiceLogEntry e1, ServiceLogEntry e2) {
            return ((Long) e1.timestamp).compareTo(e2.timestamp);
         }
      });

      return new ServiceErrorLogsResponse(list);
   }

   @Override
   public Response requestResetServiceErrorLogs(ResetServiceErrorLogsRequest r, RequestContext ctx) {
      if (logBuffer == null) {
         return new Error(CoreContract.ERROR_NOT_CONFIGURED);
      }

      logBuffer.resetErrorLogs();

      return Response.SUCCESS;
   }

   @Override
   public Response requestSetCommsLogLevel(SetCommsLogLevelRequest r, RequestContext ctx) {
      ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("comms");
      if (logger == null) {
         return new Error(CoreContract.ERROR_NOT_CONFIGURED);
      }

      logger.setLevel(ch.qos.logback.classic.Level.valueOf(r.level));

      return Response.SUCCESS;
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

   @Override
   public Response requestHostInfo(HostInfoRequest r, RequestContext ctx) {
      return new HostInfoResponse(Util.getHostName(), (byte) Metrics.getNumCores(), null);
   }

   @Override
   public Response requestHostStats(HostStatsRequest r, RequestContext ctx) {
      return new HostStatsResponse(Metrics.getLoadAverage(), Metrics.getFreeDiskSpace());
   }

}
