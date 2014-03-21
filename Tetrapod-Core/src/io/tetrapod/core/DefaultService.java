package io.tetrapod.core;

import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.service.*;

import java.util.concurrent.*;

import org.slf4j.*;

abstract public class DefaultService implements Service, BaseServiceContract.API {
   public static final Logger     logger        = LoggerFactory.getLogger(DefaultService.class);

   protected final Dispatcher     dispatcher;
   private final StructureFactory factory;
   private final Client           cluster;
   // private Server                 directConnections; // TODO: implement direct connections
   private Contract               contract;

   protected int                  entityId;
   protected int                  parentId;
   protected long                 reclaimToken;

   public DefaultService() {
      dispatcher = new Dispatcher();
      factory = new StructureFactory();
      cluster = new Client(this);
      addContracts(new BaseServiceContract());
      addPeerContracts(new TetrapodContract());
   }

   // Service protocol

   @Override
   public void startNetwork(String hostAndPort, String token) throws Exception {
      if (hostAndPort != null) {
         int ix = hostAndPort.indexOf(':');
         String host = ix < 0 ? hostAndPort : hostAndPort.substring(0, ix);
         int port = ix < 0 ? TetrapodService.DEFAULT_PRIVATE_PORT : Integer.parseInt(hostAndPort.substring(ix + 1));
         cluster.connect(host, port, dispatcher).sync();
      }
   }

   @Override
   public void onClientStart(Client client) {
      logger.debug("Sending register request");
      sendRequest(new RegisterRequest(222/*FIXME*/), Core.UNADDRESSED).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res, int errorCode) {
            if (res != null) {
               RegisterResponse r = (RegisterResponse) res;
               entityId = r.entityId;
               parentId = r.parentId;
               reclaimToken = r.reclaimToken;

               logger.info(String.format("%s My entityId is 0x%08X", cluster.getSession(), r.entityId));
               cluster.getSession().setEntityId(r.entityId);
               onRegistered();
            } else {
               fail("Unable to register", errorCode);
            }
         }
      });
   }

   abstract public void onRegistered();

   @Override
   public void onClientStop(Client client) {
      // TODO reconnection loop to handle unexpected disconnections
   }

   @Override
   public void onServerStart(Server server) {}

   @Override
   public void onServerStop(Server server) {}

   // subclass utils
   
   protected void setMainContract(Contract c) {
      addContracts(c);
      contract = c;
   }

   protected void addContracts(Contract... contracts) {
      for (Contract c : contracts) {
         c.addRequests(factory, c.getContractId());
         c.addResponses(factory, c.getContractId());
         c.addMessages(factory, c.getContractId());
      }
   }

   protected void addPeerContracts(Contract... contracts) {
      for (Contract c : contracts) {
         c.addResponses(factory, c.getContractId());
         c.addMessages(factory, c.getContractId());
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
      return contract.getName();
   }
   
   protected String getFullName() {
      String s = contract.getClass().getCanonicalName();
      return s.substring(0, s.length() - "Contract".length());
   }

   public Async sendRequest(Request req, int toEntityId) {
      return cluster.getSession().sendRequest(req, toEntityId, (byte) 30);
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
      return factory.make(contractId, structId);
   }

   @Override
   public void execute(Runnable runnable) {
      dispatcher.dispatch(runnable);
   }

   @Override
   public ScheduledFuture<?> execute(int delay, TimeUnit unit, Runnable runnable) {
      return dispatcher.dispatch(delay, unit, runnable);
   }

   @Override
   public Session getRelaySession(int entityId) {
      logger.warn("This service does not relay requests {}", entityId);
      return null;
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
