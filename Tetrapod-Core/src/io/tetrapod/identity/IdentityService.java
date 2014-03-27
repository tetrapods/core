package io.tetrapod.identity;

import io.tetrapod.core.DefaultService;
import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.identity.*;

import org.slf4j.*;

public class IdentityService extends DefaultService implements IdentityContract.API {
   private static final Logger logger = LoggerFactory.getLogger(IdentityService.class);

   public IdentityService() {
      setMainContract(new IdentityContract());
      addPeerContracts(/* non-core services we talk to: eg:*//* new WalletContract(), new StorageContract() */);
   }

   @Override
   public void onRegistered() {
      sendRequest(new RegistrySubscribeRequest(), Core.UNADDRESSED);

      // FIXME: This is no longer working -- handlers not getting triggered 
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
   }

   @Override
   public Response requestCreate(CreateRequest r, RequestContext ctx) {
      return null;
   }

   @Override
   public Response requestInfo(InfoRequest r, RequestContext ctx) {
      return null;
   }

   @Override
   public Response requestLogin(LoginRequest r, RequestContext ctx) {
      return new LoginResponse(23, "auth." + r.email + "." + r.password);
   }

   @Override
   public Response requestUpdateProperties(UpdatePropertiesRequest r, RequestContext ctx) {
      return null;
   }

}
