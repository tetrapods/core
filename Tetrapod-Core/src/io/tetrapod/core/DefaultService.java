package io.tetrapod.core;

import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.service.*;

import org.slf4j.*;

abstract public class DefaultService implements Service, BaseServiceContract.API, SessionFactory {
   public static final Logger       logger = LoggerFactory.getLogger(DefaultService.class);

   protected final Dispatcher       dispatcher;
   protected final StructureFactory structFactory;
   protected final Client           cluster;
   // protected Server                 directConnections; // TODO: implement direct connections
   protected Contract               contract;

   protected int                    entityId;
   protected int                    parentId;
   protected String                 token;

   public DefaultService() {
      dispatcher = new Dispatcher();
      structFactory = new StructureFactory();
      cluster = new Client(this);
      addContracts(new BaseServiceContract());
      addPeerContracts(new TetrapodContract());
   }

   public byte getEntityType() {
      return Core.TYPE_SERVICE;
   }

   // Service protocol

   @Override
   public void startNetwork(String hostAndPort, String token) throws Exception {
      if (hostAndPort != null) {
         this.token = token;
         int ix = hostAndPort.indexOf(':');
         String host = ix < 0 ? hostAndPort : hostAndPort.substring(0, ix);
         int port = ix < 0 ? TetrapodService.DEFAULT_PRIVATE_PORT : Integer.parseInt(hostAndPort.substring(ix + 1));
         cluster.connect(host, port, dispatcher).sync();
      }
   }

   abstract public void onRegistered();

   /**
    * Session factory for our session to our parent TetrapodService
    */
   @Override
   public Session makeSession(SocketChannel ch) {
      final Session ses = new Session(ch, DefaultService.this);
      ses.setMyEntityType(getEntityType());
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
      sendRequest(new RegisterRequest(222/*FIXME*/, token, getContractId(), getShortName()), Core.UNADDRESSED).handle(
            new ResponseHandler() {
               @Override
               public void onResponse(Response res) {
                  if (res.isError()) {
                     RegisterResponse r = (RegisterResponse) res;
                     entityId = r.entityId;
                     parentId = r.parentId;
                     token = r.token;

                     logger.info(String.format("%s My entityId is 0x%08X", cluster.getSession(), r.entityId));
                     cluster.getSession().setMyEntityId(r.entityId);
                     cluster.getSession().setTheirEntityId(r.parentId);
                     cluster.getSession().setMyEntityType(Core.TYPE_SERVICE);
                     cluster.getSession().setTheirEntityType(Core.TYPE_TETRAPOD);
                     onRegistered();
                  } else {
                     fail("Unable to register", res.errorCode());
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
         c.addRequests(structFactory, c.getContractId());
         c.addResponses(structFactory, c.getContractId());
         c.addMessages(structFactory, c.getContractId());
      }
   }

   protected void addPeerContracts(Contract... contracts) {
      for (Contract c : contracts) {
         c.addResponses(structFactory, c.getContractId());
         c.addMessages(structFactory, c.getContractId());
      }
   }

   protected int getEntityId() {
      return entityId;
   }

   protected int getParentId() {
      return parentId;
   }

   protected void fail(String reason, int errorCode) {
      // move into failure state
      // TODO implement
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

   public Async sendRequest(Request req, int toEntityId) {
      return cluster.getSession().sendRequest(req, toEntityId, (byte) 30);
   }

   public void sendMessage(Message msg, int toEntityId, int topicId) {
      cluster.getSession().sendMessage(msg, toEntityId, topicId);
   }

   // Generic handlers for all request/subscriptions

   public Response genericRequest(Request r, RequestContext ctx) {
      logger.error("unhandled request " + r.dump());
      return new Error(Core.ERROR_UNKNOWN_REQUEST);
   }

   public void genericMessage(Message message) {
      logger.error("unhandled message " + message.dump());
   }

   // Session.Help implementation

   @Override
   public Structure make(int contractId, int structId) {
      return structFactory.make(contractId, structId);
   }

   @Override
   public Dispatcher getDispatcher() {
      return dispatcher;
   }

   @Override
   public ServiceAPI getHandler(int contractId) {
      // this method allows us to have delegate objects that directly handle some contracts
      return this;
   }

   @Override
   public int getContractId() {
      return contract == null ? 0 : contract.getContractId();
   }

   // Base service implementation

   @Override
   public Response requestPause(PauseRequest r, RequestContext ctx) {
      return Response.SUCCESS;
   }

   @Override
   public Response requestUnpause(UnpauseRequest r, RequestContext ctx) {
      return Response.SUCCESS;
   }

   @Override
   public Response requestRestart(RestartRequest r, RequestContext ctx) {
      return Response.SUCCESS;
   }

   @Override
   public Response requestShutdown(ShutdownRequest r, RequestContext ctx) {
      return Response.SUCCESS;
   }

   // private methods

}
