package io.tetrapod.core;

import io.tetrapod.core.DefaultService;
import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.identity.*;
import io.tetrapod.protocol.service.*;

import org.slf4j.*;

public class TestService extends DefaultService implements BaseServiceContract.API {
   private static final Logger logger = LoggerFactory.getLogger(TestService.class);

   public TestService() {
      setMainContract(new IdentityContract());
      addPeerContracts(/* non-core services we talk to: eg:*//* new WalletContract(), new StorageContract() */);
   }

   @Override
   public void onRegistered() {
      // HACK: subscription test -- remove later:
      sendRequest(new RegistrySubscribeRequest(), Core.UNADDRESSED);
      addSubscriptionHandler(new TetrapodContract.Registry(), new TetrapodContract.Registry.API() {
         @Override
         public void messageTopicUnsubscribed(TopicUnsubscribedMessage m, MessageContext ctx) {
            logger.info("Dispatched message: {}", m.dump());
         }

         @Override
         public void messageTopicUnpublished(TopicUnpublishedMessage m, MessageContext ctx) {
            logger.info("Dispatched message: {}", m.dump());
         }

         @Override
         public void messageTopicSubscribed(TopicSubscribedMessage m, MessageContext ctx) {
            logger.info("Dispatched message: {}", m.dump());
         }

         @Override
         public void messageTopicPublished(TopicPublishedMessage m, MessageContext ctx) {
            logger.info("Dispatched message: {}", m.dump());
         }

         @Override
         public void messageEntityUnregistered(EntityUnregisteredMessage m, MessageContext ctx) {
            logger.info("Dispatched message: {}", m.dump());
         }

         @Override
         public void genericMessage(Message message, MessageContext ctx) {
            logger.info("GENERIC Dispatched message: {}", message.dump());
         }

         @Override
         public void messageEntityRegistered(EntityRegisteredMessage m, MessageContext ctx) {
            logger.info("Dispatched message: {}", m.dump());
         }

         @Override
         public void messageEntityUpdated(EntityUpdatedMessage m, MessageContext ctx) {
            logger.info("Dispatched message: {}", m.dump());
         }
      });

      updateStatus(status & ~Core.STATUS_STARTING);
   }

   @Override
   public void onShutdown(boolean restarting) {}

   @Override
   public Response requestServiceIcon(ServiceIconRequest r, RequestContext ctx) {
      return new ServiceIconResponse("media/identity.png");
   }
}
