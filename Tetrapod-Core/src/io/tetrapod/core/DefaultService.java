package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.UNADDRESSED;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.service.*;

import java.util.*;

import org.slf4j.*;

public class DefaultService implements Service, BaseServiceContract.API, SessionFactory {
   private static final Logger   logger          = LoggerFactory.getLogger(DefaultService.class);

   protected final Dispatcher    dispatcher;
   protected Client              clusterClient;
   protected Contract            contract;

   protected boolean             terminated;
   protected int                 entityId;
   protected int                 parentId;
   protected String              token;
   protected int                 status;

   private final MessageHandlers messageHandlers = new MessageHandlers();

   public DefaultService() {
      status |= Core.STATUS_STARTING;
      dispatcher = new Dispatcher();
      clusterClient = new Client(this);
      addContracts(new BaseServiceContract());
      addPeerContracts(new TetrapodContract());
   }

   public byte getEntityType() {
      return Core.TYPE_SERVICE;
   }

   // Service protocol

   @Override
   public void startNetwork(ServerAddress server, String token) throws Exception {
      this.token = token;
      clusterClient.connect(server.host, server.port, dispatcher).sync();
   }

   /**
    * Called after registration is complete and this service has a valid entityId and is free to make requests into the cluster. Default
    * implementation is to do nothing.
    */
   public void onRegistered() {}

   /**
    * Called before shutting down. Default implementation is to do nothing. Subclasses are expecting to close any resources they opened (for
    * example database connections or file handles).
    * 
    * @param restarting true if we are shutting down in order to restart
    */
   public void onShutdown(boolean restarting) {}

   public void shutdown(boolean restarting) {
      updateStatus(status | Core.STATUS_STOPPING);
      onShutdown(restarting);
      if (restarting) {
         clusterClient.close();
         dispatcher.shutdown();
         setTerminated(true);
         try {
            Launcher.relaunch(token);
         } catch (Exception e) {
            logger.error(e.getMessage(), e);
         }
      } else {
         sendRequest(new UnregisterRequest(getEntityId()), Core.UNADDRESSED).handle(new ResponseHandler() {
            @Override
            public void onResponse(Response res) {
               clusterClient.close();
               dispatcher.shutdown();
               setTerminated(true);
            }
         });
      }
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
      sendRequest(new RegisterRequest(222/* FIXME */, token, getContractId(), getShortName(), status), Core.UNADDRESSED).handle(
            new ResponseHandler() {
               @Override
               public void onResponse(Response res) {
                  if (res.isError()) {
                     fail("Unable to register {}", res.errorCode());
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
                     registerServiceInformation();
                     onRegistered();
                  }
               }
            });
   }

   public void onDisconnectedFromCluster() {
      // TODO reconnection loop to handle unexpected disconnections
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

   protected int getEntityId() {
      return entityId;
   }

   protected int getParentId() {
      return parentId;
   }

   public boolean isShuttingDown() {
      return (status & Core.STATUS_STOPPING) != 0;
   }

   public synchronized boolean isTerminated() {
      return terminated;
   }

   private synchronized void setTerminated(boolean val) {
      logger.info("TERMINATED");
      terminated = val;
   }

   protected void updateStatus(int status) {
      synchronized (this) {
         this.status = status;
      }
      if (clusterClient.isConnected()) {
         sendRequest(new ServiceStatusUpdateRequest(status), Core.UNADDRESSED);
      }
   }

   protected void fail(Throwable error) {
      logger.error(error.getMessage(), error);
      updateStatus(status | Core.STATUS_FAILED);
   }

   protected void fail(String reason, int errorCode) {
      logger.error("FAIL: {} {}", reason, errorCode);
      updateStatus(status | Core.STATUS_FAILED);
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

   public Response sendPendingRequest(Request req, PendingResponseHandler handler) {
      return clusterClient.getSession().sendPendingRequest(req, Core.UNADDRESSED, (byte) 30, handler);
   }

   public Response sendPendingRequest(Request req, int toEntityId, PendingResponseHandler handler) {
      return clusterClient.getSession().sendPendingRequest(req, toEntityId, (byte) 30, handler);
   }

   public Async sendRequest(Request req) {
      return clusterClient.getSession().sendRequest(req, Core.UNADDRESSED, (byte) 30);
   }

   public Async sendRequest(Request req, int toEntityId) {
      return clusterClient.getSession().sendRequest(req, toEntityId, (byte) 30);
   }

   public void sendMessage(Message msg, int toEntityId, int topicId) {
      clusterClient.getSession().sendMessage(msg, toEntityId, topicId);
   }

   public void sendBroadcastMessage(Message msg, int topicId) {
      clusterClient.getSession().sendBroadcastMessage(msg, topicId);
   }

   public void subscribe(int topicId, int entityId) {
      sendMessage(new TopicSubscribedMessage(getEntityId(), topicId, entityId), UNADDRESSED, topicId);
   }

   public void unsubscribe(int topicId, int entityId) {
      sendMessage(new TopicUnsubscribedMessage(getEntityId(), topicId, entityId), UNADDRESSED, topicId);
   }

   // Generic handlers for all request/subscriptions

   public Response genericRequest(Request r, RequestContext ctx) {
      logger.error("unhandled request " + r.dump());
      return new Error(TetrapodContract.ERROR_UNKNOWN_REQUEST);
   }

   public void genericMessage(Message message) {
      logger.error("unhandled message " + message.dump());
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

   // Base service implementation

   @Override
   public Response requestPause(PauseRequest r, RequestContext ctx) {
      updateStatus(status | Core.STATUS_PAUSED);
      return Response.SUCCESS;
   }

   @Override
   public Response requestUnpause(UnpauseRequest r, RequestContext ctx) {
      updateStatus(status & ~Core.STATUS_PAUSED);
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
   public Response requestServiceIcon(ServiceIconRequest r, RequestContext ctx) {
      return new ServiceIconResponse("media/gear.gif");
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
         sendRequest(asi, Core.UNADDRESSED).handle(ResponseHandler.LOGGER);
      }
   }

}
