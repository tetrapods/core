package  io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;
import io.tetrapod.protocol.core.WebRoute;

/**
 * The core tetrapod service
 */

@SuppressWarnings("unused")
public class TetrapodContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "Tetrapod";
   public static final int CONTRACT_ID = 1;
   
   public static interface API extends
      AddWebRoutesRequest.Handler,
      PublishRequest.Handler,
      RegisterRequest.Handler,
      RegistrySubscribeRequest.Handler,
      ServiceStatusUpdateRequest.Handler
      {}
   
   public void addRequests(StructureFactory factory) {
      factory.add(new AddWebRoutesRequest());
      factory.add(new PublishRequest());
      factory.add(new RegisterRequest());
      factory.add(new RegistrySubscribeRequest());
      factory.add(new ServiceStatusUpdateRequest());
   }
   
   public void addResponses(StructureFactory factory) {
      factory.add(new PublishResponse());
      factory.add(new RegisterResponse());
   }
   
   public void addMessages(StructureFactory factory) {
      factory.add(new EntityRegisteredMessage());
      factory.add(new EntityUnregisteredMessage());
      factory.add(new EntityUpdatedMessage());
      factory.add(new ServiceAddedMessage());
      factory.add(new ServiceRemovedMessage());
      factory.add(new ServiceStatsMessage());
      factory.add(new ServiceUpdatedMessage());
      factory.add(new TopicPublishedMessage());
      factory.add(new TopicSubscribedMessage());
      factory.add(new TopicUnpublishedMessage());
      factory.add(new TopicUnsubscribedMessage());
   }
   
   public String getName() {
      return TetrapodContract.NAME;
   } 
   
   public int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }
   
   public WebRoute[] getWebRoutes() {
      return new WebRoute[] {
         
      };
   }

   public static class Registry extends Contract {
      public static interface API extends
         EntityRegisteredMessage.Handler,
         EntityUnregisteredMessage.Handler,
         EntityUpdatedMessage.Handler,
         TopicPublishedMessage.Handler,
         TopicSubscribedMessage.Handler,
         TopicUnpublishedMessage.Handler,
         TopicUnsubscribedMessage.Handler
         {}
         
      public void addMessages(StructureFactory factory, int dynamicId) {
         factory.add(new EntityRegisteredMessage());
         factory.add(new EntityUnregisteredMessage());
         factory.add(new EntityUpdatedMessage());
         factory.add(new TopicPublishedMessage());
         factory.add(new TopicSubscribedMessage());
         factory.add(new TopicUnpublishedMessage());
         factory.add(new TopicUnsubscribedMessage());
      }
      
      public String getName() {
         return TetrapodContract.NAME;
      }
      
      public int getContractId() {
         return TetrapodContract.CONTRACT_ID;
      } 
       
   }
      
   public static class Services extends Contract {
      public static interface API extends
         ServiceAddedMessage.Handler,
         ServiceRemovedMessage.Handler,
         ServiceStatsMessage.Handler,
         ServiceUpdatedMessage.Handler
         {}
         
      public void addMessages(StructureFactory factory, int dynamicId) {
         factory.add(new ServiceAddedMessage());
         factory.add(new ServiceRemovedMessage());
         factory.add(new ServiceStatsMessage());
         factory.add(new ServiceUpdatedMessage());
      }
      
      public String getName() {
         return TetrapodContract.NAME;
      }
      
      public int getContractId() {
         return TetrapodContract.CONTRACT_ID;
      } 
       
   }
      
   public static final int ERROR_INVALID_ENTITY = 5084230; 
   
   /**
    * Caller does not have sufficient rights to call this Request
    */
   public static final int ERROR_INVALID_RIGHTS = 7; 
   public static final int ERROR_NOT_PARENT = 2219555; 
   public static final int ERROR_NOT_READY = 12438466; 
   
   /**
    * Protocol versions are not compatible
    */
   public static final int ERROR_PROTOCOL_MISMATCH = 5; 
   
   /**
    * Unable to deserialize the request
    */
   public static final int ERROR_SERIALIZATION = 4; 
   
   /**
    * No service exists to which to relay the request
    */
   public static final int ERROR_SERVICE_UNAVAILABLE = 2; 
   
   /**
    * Request timed out without returning a response
    */
   public static final int ERROR_TIMEOUT = 3; 
   
   /**
    * catch all error
    */
   public static final int ERROR_UNKNOWN = 1; 
   
   /**
    * Service exists and received request, but doen't know how to handle it
    */
   public static final int ERROR_UNKNOWN_REQUEST = 6; 
}
