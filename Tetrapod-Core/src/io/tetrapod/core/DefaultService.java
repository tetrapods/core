package io.tetrapod.core;

import io.netty.channel.ChannelFuture;
import io.tetrapod.core.protocol.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.utils.Properties;

import java.util.concurrent.*;

import org.slf4j.*;

public class DefaultService implements Service {
   public static final Logger logger = LoggerFactory.getLogger(DefaultService.class);

   private final Dispatcher    dispatcher;
   private final StructureFactory factory;
   private Client cluster;
   private Server directConnections;
   private Contract contract;
   private Contract[] peerContracts;

   public DefaultService() {
      dispatcher = new Dispatcher();
      factory = new StructureFactory();
   }
   
   public void serviceInit(Properties props) {
      // add in root level contracts
      addContract(new TetrapodContract(), TetrapodContract.CONTRACT_ID);
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

   protected void setPeerContracts(Contract ... contracts) {
      this.peerContracts = contracts;
   }
   
   protected void messageContractAdded(String name, int contractId) {
      if (contract.getName().equals(name))
         addContract(contract, contractId);
      for (Contract c : peerContracts)
         if (c.getName().equals(name))
            addContract(c, contractId);
   }
   
   private void addContract(Contract c, int contractId) {
      c.addRequests(factory, contractId);
      c.addResponses(factory, contractId);
      c.addMessages(factory, contractId);
   }

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
   public ServiceAPI getHandler(int contractId) {
      // this method allows us to have delegate objects that directly handle some contracts
      return this;
   }

   @Override
   public Response genericRequest(Request r) {
      logger.error("unhandled request " + r.dump());
      return new Error(Request.ERROR_UNKNOWN_REQUEST);
   }


}
