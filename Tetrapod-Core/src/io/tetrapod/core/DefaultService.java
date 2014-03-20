package io.tetrapod.core;

import io.netty.channel.ChannelFuture;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.utils.Properties;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.service.*;

import java.util.concurrent.*;

import org.slf4j.*;

public class DefaultService implements Service, BaseServiceContract.API, TetrapodContract.ServiceInfo.API {
   public static final Logger     logger = LoggerFactory.getLogger(DefaultService.class);

   protected final Dispatcher     dispatcher;
   private final StructureFactory factory;
   private final Client           cluster;
   private Contract               contract;
   private Contract[]             peerContracts;
   private long                   reclaimToken;

   public DefaultService() {
      dispatcher = new Dispatcher();
      factory = new StructureFactory();
      cluster = new Client(this);
   }

   public void serviceInit(Properties props) {
      // add in root level contracts
      addContract(new TetrapodContract(), TetrapodContract.CONTRACT_ID); // FIXME: addPeerContract?
      addContract(new BaseServiceContract(), BaseServiceContract.CONTRACT_ID);
   }

   public void networkInit(Properties props) throws Exception {
      cluster.connect(props.optString("clusterHost", "localhost"), props.optInt("clusterPort", TetrapodService.DEFAULT_PRIVATE_PORT),
            dispatcher).sync();
      cluster.getSession().addSessionListener(new Session.Listener() {
         @Override
         public void onSessionStop(Session ses) {
            // TODO: reconnect loop needed to handle disconnections            
         }

         @Override
         public void onSessionStart(Session ses) {
            register();
         }
      });
      register();

      // TODO: Lets worry about this much later...
      //      directConnections = new Server(props.optInt("directConnectPort", 11124), this);
      //      ChannelFuture f = directConnections.start();
      //      f.sync();
   }

   public int getEntityId() {
      return cluster.getSession().getEntityId();
   }

   protected void setContract(Contract contract) {
      this.contract = contract;
   }

   protected void setPeerContracts(Contract... contracts) {
      this.peerContracts = contracts;
   }

   private void addContract(Contract c, int contractId) {
      c.addRequests(factory, contractId);
      c.addResponses(factory, contractId);
      c.addMessages(factory, contractId);
   }

   public Async sendRequest(Request req, int toEntityId) {
      return cluster.getSession().sendRequest(req, toEntityId, (byte) 30);
   }

   // Generic handlers for all request/subscriptions

   @Override
   public Response genericRequest(Request r, RequestContext ctx) {
      logger.error("unhandled request " + r.dump());
      return new Error(Request.ERROR_UNKNOWN_REQUEST);
   }

   @Override
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

   // ServiceInfo subscription

   @Override
   public void messageServiceAdded(ServiceAddedMessage m) {
      if (contract.getName().equals(m.name))
         addContract(contract, m.contractId);
      for (Contract c : peerContracts)
         if (c.getName().equals(m.name))
            addContract(c, m.contractId);
   }

   /**
    * Register as an entity with the cluster
    */
   private void register() {
      // TODO: reclaims
      sendRequest(new RegisterRequest(666/*FIXME*/), 0).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res, int errorCode) {
            if (res != null) {
               logger.info("{}", res.dump());
               RegisterResponse r = (RegisterResponse) res;
               logger.info(String.format("My ID is 0x%08X", r.entityId));
               reclaimToken = r.reclaimToken;
               cluster.getSession().setEntityId(r.entityId);
            }
         }
      });
   }

}
