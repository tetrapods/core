package io.tetrapod.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.utils.Properties;
import io.tetrapod.protocol.core.*;

import java.util.concurrent.*;

import io.tetrapod.protocol.service.*;

import org.slf4j.*;

public class DefaultService implements Service, BaseServiceContract.API, TetrapodContract.ServiceInfo.API {
   public static final Logger     logger = LoggerFactory.getLogger(DefaultService.class);

   private final Dispatcher       dispatcher;
   private final StructureFactory factory;
   private Client                 cluster;
   private Server                 directConnections;
   private Contract               contract;
   private Contract[]             peerContracts;

   public DefaultService() {
      dispatcher = new Dispatcher();
      factory = new StructureFactory();
   }

   public void serviceInit(Properties props) {
      // add in root level contracts
      addContract(new TetrapodContract(), TetrapodContract.CONTRACT_ID); // FIXME: addPeerContract?
      addContract(new BaseServiceContract(), BaseServiceContract.CONTRACT_ID);
   }

   public void networkInit(Properties props) throws Exception {
      cluster = new Client(this);
      directConnections = new Server(props.optInt("directConnectPort", 11124), this);
      ChannelFuture f = directConnections.start();
      cluster.connect(props.optString("clusterHost", "localhost"), props.optInt("clusterPort", 11123), dispatcher).sync();
      f.sync();
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

   // Generic handlers for all request/subscriptions

   @Override
   public Response genericRequest(Request r) {
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
   public void relayRequest(RequestHeader header, ByteBuf in, Session fromSession) {
      logger.warn("Could not route request for {} to {}", fromSession, header);
   }

   @Override
   public ServiceAPI getHandler(int contractId) {
      // this method allows us to have delegate objects that directly handle some contracts
      return this;
   }

   // Base service implementation

   @Override
   public Response requestPause(PauseRequest r) {
      return Response.SUCCESS;
   }

   @Override
   public Response requestUnpause(UnpauseRequest r) {
      return Response.SUCCESS;
   }

   @Override
   public Response requestRestart(RestartRequest r) {
      return Response.SUCCESS;
   }

   @Override
   public Response requestShutdown(ShutdownRequest r) {
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

}
