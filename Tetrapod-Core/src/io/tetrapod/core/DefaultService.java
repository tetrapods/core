package io.tetrapod.core;

import static io.tetrapod.protocol.core.CoreContract.*;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.ConnectException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import io.tetrapod.core.tasks.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Timer.Context;

import ch.qos.logback.classic.LoggerContext;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.pubsub.Publisher;
import io.tetrapod.core.pubsub.Topic;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.utils.*;
import io.tetrapod.protocol.core.*;

public class DefaultService
      implements Service, Fail.FailHandler, CoreContract.API, SessionFactory, EntityMessage.Handler, TetrapodContract.Cluster.API {

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
   public final String                     buildName;
   protected final LogBuffer               logBuffer;
   protected SSLContext                    sslContext;
   private List<SubService>                subServices     = new ArrayList<>();

   protected ServiceConnector              serviceConnector;

   protected final ServiceStats            stats;
   protected boolean                       startPaused;
   public final SecureRandom               random          = new SecureRandom();

   private final LinkedList<ServerAddress> clusterMembers  = new LinkedList<>();

   private final MessageHandlers           messageHandlers = new MessageHandlers();

   private final Publisher                 publisher       = new Publisher(this);
   private long                            dependencyCheckLogThreshold;

   public DefaultService() {
      this(null);
   }

   public DefaultService(Contract mainContract) {
      logBuffer = (LogBuffer) ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("ROOT").getAppender("BUFFER");
      String m = getStartLoggingMessage();
      dependencyCheckLogThreshold = System.currentTimeMillis() + 10000;
      logger.info(m);
      Session.commsLog.info(m);
      Fail.handler = this;
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
            sslContext = Util.createSSLContext(new FileInputStream(Util.getProperty("tetrapod.jks.file", "cfg/tetrapod.jks")),
                  Util.getProperty("tetrapod.jks.pwd", "4pod.dop4").toCharArray());
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
         @Override
         public void run() {
            logger.info("Shutdown Hook");
            if (!isShuttingDown()) {
               shutdown(false);
            }
         }
      });

      String build = "Unknown";
      try {
         build = Util.readFileAsString(new File("build_name.txt"));
      } catch (IOException e) {}
      buildName = build;

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
    * Called after we've registered and dependencies are all available
    */
   public void onReadyToServe() {}

   private void onServiceRegistered() {
      registerServiceInformation(this.contract);
      for (SubService subService : subServices) {
         registerServiceInformation(subService.getContract());
      }
      stats.publishTopic();
      resetServiceConnector(true);
   }

   public boolean dependenciesReady(boolean logIfNotReady) {
      return services == null || services.checkDependencies(dependencies, logIfNotReady);
   }

   public boolean dependenciesReady(Set<Integer> deps) {
      return services.checkDependencies(deps, false);
   }

   private final Object checkDependenciesLock = new Object();

   public void checkDependencies() {
      synchronized (checkDependenciesLock) {
         if (!isShuttingDown() && isStartingUp()) {
            logger.info("Checking Dependencies...");
            if (dependenciesReady(dependencyCheckLogThreshold < System.currentTimeMillis())) {
               try {
                  if (startPaused) {
                     setStatus(Core.STATUS_PAUSED);
                  }
                  if (getEntityType() != Core.TYPE_TETRAPOD) {
                     AdminAuthToken.setSecret(Util.getProperty(AdminAuthToken.SHARED_SECRET_KEY));
                  }
                  onReadyToServe();
               } catch (Throwable t) {
                  fail(t);
               }
               // ok, we're good to go
               logger.info("Startup tasks done");
               clearStatus(Core.STATUS_STARTING);
               onStarted();
               if (startPaused) {
                  onPaused();
                  startPaused = false; // only start paused once
               }
            } else {
               dispatcher.dispatch(1, TimeUnit.SECONDS, () -> checkDependencies());
            }
         }
      }
   }

   protected void resetServiceConnector(boolean start) {
      logger.info("resetServiceConnector({})", start);
      if (serviceConnector != null) {
         serviceConnector.shutdown();
         serviceConnector = null;
      }
      if (start) {
         serviceConnector = new ServiceConnector(this, sslContext);
      }
   }

   /**
    * Periodically checks service health, updates metrics
    */
   private void checkHealth() {
      if (!isShuttingDown()) {
         try {
            if (dispatcher.workQueueSize.getCount() > 0) {
               logger.warn("DISPATCHER QUEUE SIZE = {} ({} threads busy)", dispatcher.workQueueSize.getCount(),
                     dispatcher.getActiveThreads());
            }

            int status = 0;
            if (logBuffer.hasErrors()) {
               status |= Core.STATUS_ERRORS;
            }
            if (logBuffer.hasWarnings()) {
               status |= Core.STATUS_WARNINGS;
            }

            synchronized (this) {
               if (needsStatusUpdate) {
                  // if a status update previously failed, we try a hamfisted approach here to clobber-fix everything (except GONE state).
                  needsStatusUpdate = false;
                  sendDirectRequest(new ServiceStatusUpdateRequest(getStatus(), ~Core.STATUS_GONE)).handle(res -> {
                     if (res.isError()) {
                        needsStatusUpdate = true;
                     }
                  });
               } else {
                  setStatus(status, Core.STATUS_ERRORS | Core.STATUS_WARNINGS);
               }
            }

         } finally {
            dispatcher.dispatch(1, TimeUnit.SECONDS, () -> checkHealth());
         }
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
      setStatus(Core.STATUS_STOPPING);
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
         if (getEntityId() != 0 && clusterClient.getSession() != null) {
            sendDirectRequest(new UnregisterRequest(0, null, getEntityId())).handle(res -> {
               clusterClient.close();
               dispatcher.shutdown();
               setTerminated(true);
            });
         } else {
            dispatcher.shutdown();
            setTerminated(true);
         }
      }

      // If JVM doesn't gracefully terminate after 1 minute, explicitly kill the process
      final Thread hitman = new Thread(() -> {
         Util.sleep(Util.ONE_MINUTE);
         logger.warn("Service did not complete graceful termination. Force Killing JVM.");
         final Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();
         for (Thread t : map.keySet()) {
            logger.warn("{}", t);
         }
         System.exit(1);
      }, "Hitman");
      hitman.setDaemon(true);
      hitman.start();
   }

   public ServiceCache getServiceCache() {
      return services;
   }

   public Publisher getPublisher() {
      return publisher;
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
            logger.info("Connection to tetrapod closed");
            onDisconnectedFromCluster();
            services.clear();
            resetServiceConnector(false);
            if (!isShuttingDown()) {
               dispatcher.dispatch(3, TimeUnit.SECONDS, () -> connectToCluster(1));
            }
            publisher.resetTopics();
         }

         @Override
         public void onSessionStart(Session ses) {
            onConnectedToCluster();
         }
      });
      return ses;
   }

   protected void onConnectedToCluster() {
      final Request req = new RegisterRequest(token, getContractId(), getShortName(), getStatus(), Util.getHostName(), buildName);
      sendDirectRequest(req).handle(res -> {
         if (res.isError()) {
            logger.error("Unable to register: " + req.dump() + " ==> " + res);
            clusterClient.close();
         } else {
            RegisterResponse r = (RegisterResponse) res;
            entityId = r.entityId;
            parentId = r.parentId;
            token = r.token;

            clusterClient.getSession().setMyEntityId(r.entityId);
            clusterClient.getSession().setTheirEntityId(r.parentId);
            clusterClient.getSession().setMyEntityType(getEntityType());
            clusterClient.getSession().setTheirEntityType(Core.TYPE_TETRAPOD);
            logger.info(String.format("%s My entityId is 0x%08X", clusterClient.getSession(), r.entityId));
            onServiceRegistered();
         }
      });
   }

   public void onDisconnectedFromCluster() {
      // override
   }

   public boolean isConnected() {
      return clusterClient.isConnected();
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
               } finally {
                  clusterMembers.addLast(server);
               }
            }
         }

         // schedule a retry
         dispatcher.dispatch(retrySeconds, TimeUnit.SECONDS, () -> connectToCluster(retrySeconds));
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

   /**
    * Sets these status bits to true
    */
   protected void setStatus(int bits) {
      setStatus(bits, bits);
   }

   /**
    * Clear these status bits
    */
   protected void clearStatus(int bits) {
      setStatus(0, bits);
   }

   private boolean needsStatusUpdate = false;

   protected void setStatus(int bits, int mask) {
      boolean changed = false;
      synchronized (this) {
         int status = (this.status & ~mask) | bits;
         changed = this.status != status;
         this.status = status;
      }

      if (changed && clusterClient.isConnected()) {
         sendDirectRequest(new ServiceStatusUpdateRequest(bits, mask)).handle(res -> {
            if (res.isError()) {
               needsStatusUpdate = true;
            }
         });
      }
   }

   @Override
   public void fail(Throwable error) {
      logger.error(error.getMessage(), error);
      setStatus(Core.STATUS_FAILED);
      onPaused();
   }

   @Override
   public void fail(String reason) {
      logger.error("FAIL: {}", reason);
      setStatus(Core.STATUS_FAILED);
      if (!isPaused()) {
         onPaused();
      }
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

   public String getShortName() {
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
      //return (long) dispatcher.requestTimes.getSnapshot().getMean() / 1000000L;
      return Math.round(dispatcher.requestTimes.getOneMinuteRate());
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
      final ServiceAPI svc = getServiceHandler(header.contractId, req.getStructId());
      if (svc != null) {
         final long start = System.nanoTime();
         final Context context = dispatcher.requestTimes.time();
         if (!dispatcher.dispatch(() -> {
            final long dispatchTime = System.nanoTime();

            Runnable onResult = () -> {
               final long elapsed = System.nanoTime() - dispatchTime;
               stats.recordRequest(header.fromParentId, req, elapsed, async.getErrorCode());
               context.stop();
               dispatcher.requestsHandledCounter.mark();
               if (Util.nanosToMillis(elapsed) > 1000) {
                  logger.warn("Request took {} {} millis", req, Util.nanosToMillis(elapsed));
               }
            };

            try {
               if (Util.nanosToMillis(dispatchTime - start) > 2500) {
                  if ((getStatus() & Core.STATUS_OVERLOADED) == 0) {
                     logger.warn("Service is overloaded. Dispatch time is {}ms", Util.nanosToMillis(dispatchTime - start));
                  }
                  // If it took a while to get dispatched, so set STATUS_OVERLOADED flag as a back-pressure signal
                  setStatus(Core.STATUS_OVERLOADED);
               } else {
                  clearStatus(Core.STATUS_OVERLOADED);
               }

               final RequestContext ctx = fromSession != null ? new SessionRequestContext(header, fromSession)
                     : new InternalRequestContext(header, new ResponseHandler() {
                        @Override
                        public void onResponse(Response res) {
                           try {
                              assert res != Response.PENDING;
                              onResult.run();
                              async.setResponse(res);
                           } catch (Throwable e) {
                              logger.error(e.getMessage(), e);
                              async.setResponse(new Error(ERROR_UNKNOWN));
                           }
                        }
                     });
               Response res = req.securityCheck(ctx);
               if (res == null) {
                  res = req.dispatch(svc, ctx);
               }
               if (res != null) {
                  if (res != Response.PENDING) {
                     async.setResponse(res);
                  }
               } else {
                  async.setResponse(new Error(ERROR_UNKNOWN));
               }
            } catch (ErrorResponseException e) {
               async.setResponse(new Error(e.errorCode));
            } catch (Throwable e) {
               logger.error(e.getMessage(), e);
               async.setResponse(new Error(ERROR_UNKNOWN));
            } finally {
               if (async.getErrorCode() != -1) {
                  onResult.run();
               }
            }
         }, Session.DEFAULT_OVERLOAD_THRESHOLD)) {
            // too many items queued, full-force back-pressure
            async.setResponse(new Error(ERROR_SERVICE_OVERLOADED));
            setStatus(Core.STATUS_OVERLOADED);
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

   public void sendRequest(Request req, Async.IResponseHandler handler) {
      sendRequest(req).handle(handler);
   }

   public <TResp extends Response, TValue> Task<ResponseAndValue<TResp, TValue>> sendRequestTask(Request req, TValue value) {
      if (serviceConnector != null) {
         return serviceConnector.sendRequest(req, Core.UNADDRESSED).asTask(value);
      }
      return clusterClient.getSession().sendRequest(req, Core.UNADDRESSED, (byte) 30).asTask(value);
   }

   public <TResp extends Response> Task<TResp> sendRequestTask(Request req) {
      if (serviceConnector != null) {
         return serviceConnector.sendRequest(req, Core.UNADDRESSED).asTask();
      }
      return clusterClient.getSession().sendRequest(req, Core.UNADDRESSED, (byte) 30).asTask();
   }

   public <TResp extends Response, TValue> Task<ResponseAndValue<TResp, TValue>> sendRequestTask(RequestWithResponse<TResp> req,
         TValue value) {
      if (serviceConnector != null) {
         return serviceConnector.sendRequest(req, Core.UNADDRESSED).asTask(value);
      }
      return clusterClient.getSession().sendRequest(req, Core.UNADDRESSED, (byte) 30).asTask(value);
   }

   public <TResp extends Response> Task<TResp> sendRequestTask(RequestWithResponse<TResp> req) {
      if (serviceConnector != null) {
         return serviceConnector.sendRequest(req, Core.UNADDRESSED).asTask();
      }
      return clusterClient.getSession().sendRequest(req, Core.UNADDRESSED, (byte) 30).asTask();
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

   public boolean isServiceExistant(int entityId) {
      return services.isServiceExistant(entityId);
   }

   public void sendMessage(Message msg, int toEntityId, int childId) {
      if (serviceConnector != null
            && (serviceConnector.hasService(toEntityId) || (services != null && services.isServiceExistant(toEntityId)))) {
         serviceConnector.sendMessage(msg, toEntityId, childId);
      } else {
         clusterClient.getSession().sendMessage(msg, toEntityId, childId);
      }
   }

   public void sendAltBroadcastMessage(Message msg, int altId) {
      clusterClient.getSession().sendAltBroadcastMessage(msg, altId);
   }

   public boolean sendBroadcastMessage(Message msg, int toEntityId, int topicId) {
      if (serviceConnector != null) {
         return serviceConnector.sendBroadcastMessage(msg, toEntityId, topicId);
      } else {
         //clusterClient.getSession().sendTopicBroadcastMessage(msg, toEntityId, topicId);
         return false;
      }
   }

   public boolean sendPrivateMessage(Message msg, int toEntityId, int toChildId, int topicId) {
      if (serviceConnector != null
            && (serviceConnector.hasService(toEntityId) || (services != null && services.isServiceExistant(toEntityId)))) {
         return serviceConnector.sendMessage(msg, toEntityId, toChildId);
      } else {
         clusterClient.getSession().sendMessage(msg, toEntityId, toChildId);
         return true;
      }
   }

   public Topic publishTopic() {
      return publisher.publish();
   }

   /**
    * Subscribe an entity to the given topic. If once is true, tetrapod won't subscribe them a second time
    */
   public void subscribe(int topicId, int entityId, int childId, boolean once) {
      publisher.subscribe(topicId, entityId, childId, once);
   }

   public void subscribe(int topicId, int entityId, int childId) {
      subscribe(topicId, entityId, childId, false);
   }

   public void unsubscribe(int topicId, int entityId, int childId) {
      publisher.unsubscribe(topicId, entityId, childId);
   }

   public void unpublish(int topicId) {
      publisher.unpublish(topicId);
   }

   // Generic handlers for all request/subscriptions

   @Override
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

   private ServiceAPI getServiceHandler(int contractId, int structId) {
      // this method allows us to have delegate objects that directly handle some contracts
      if (!Util.isEmpty(subServices) && contractId == contract.getContractId() || !handlesStuct(contract, structId)) {
         for (SubService subService : subServices) {
            if (handlesStuct(subService.getContract(), structId))
               return subService;
         }
      }
      return this;
   }

   private boolean handlesStuct(Contract c, int structId) {
      for (Structure structure : c.getRequests()) {
         if (structId == structure.getStructId())
            return true;
      }
      for (Structure structure : c.getMessages()) {
         if (structId == structure.getStructId())
            return true;
      }
      return false;
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
      logger.info("******** {}", m.dump());
      clusterMembers.add(new ServerAddress(m.host, m.servicePort));
      if (serviceConnector != null) {
         serviceConnector.getDirectServiceInfo(m.entityId).considerConnecting();
      }
   }

   @Override
   public void messageClusterPropertyAdded(ClusterPropertyAddedMessage m, MessageContext ctx) {
      logger.info("******** {}", m.dump());
      Util.setProperty(m.property.key, m.property.val);
   }

   @Override
   public void messageClusterPropertyRemoved(ClusterPropertyRemovedMessage m, MessageContext ctx) {
      logger.info("******** {}", m.dump());
      Util.clearProperty(m.key);
   }

   @Override
   public void messageClusterSynced(ClusterSyncedMessage m, MessageContext ctx) {
      Metrics.init(getMetricsPrefix());
      checkDependencies();
   }

   // private methods

   protected void registerServiceInformation(Contract contract) {
      if (this.contract != null) {
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
         asi.info.subContractId = contract.getSubContractId();
         sendDirectRequest(asi).handle(ResponseHandler.LOGGER);
      }
   }

   // Base service implementation

   @Override
   public Response requestPause(PauseRequest r, RequestContext ctx) {
      setStatus(Core.STATUS_PAUSED);
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
      clearStatus(Core.STATUS_PAUSED);
      onUnpaused();
      return Response.SUCCESS;
   }

   @Override
   public Response requestRestart(RestartRequest r, RequestContext ctx) {
      dispatcher.dispatch(() -> shutdown(true));
      return Response.SUCCESS;
   }

   @Override
   public Response requestShutdown(ShutdownRequest r, RequestContext ctx) {
      dispatcher.dispatch(() -> shutdown(false));
      return Response.SUCCESS;
   }

   @Override
   public Response requestServiceDetails(ServiceDetailsRequest r, RequestContext ctx) {
      return new ServiceDetailsResponse(getServiceIcon(), getServiceMetadata(), getServiceCommands());
   }

   @Override
   public Response requestServiceStatsSubscribe(ServiceStatsSubscribeRequest r, RequestContext ctx) {
      stats.subscribe(ctx.header.fromParentId, ctx.header.fromChildId);
      return Response.SUCCESS;
   }

   @Override
   public Response requestServiceStatsUnsubscribe(ServiceStatsUnsubscribeRequest r, RequestContext ctx) {
      stats.unsubscribe(ctx.header.fromParentId, ctx.header.fromChildId);
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
      Collections.sort(list, (e1, e2) -> ((Long) e1.timestamp).compareTo(e2.timestamp));

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

   @Override
   public Response requestServiceRequestStats(ServiceRequestStatsRequest r, RequestContext ctx) {
      return stats.getRequestStats(r.domain, r.limit, r.minTime, r.sortBy);
   }

   @Override
   public void messageWebRootAdded(WebRootAddedMessage m, MessageContext ctx) {}

   @Override
   public void messageWebRootRemoved(WebRootRemovedMessage m, MessageContext ctx) {}

   @Override
   public void messageRegisterContract(RegisterContractMessage m, MessageContext ctx) {}

   protected void addSubService(SubService subService) {
      subServices.add(subService);
      addPeerContracts(subService.getContract());
   }

}
