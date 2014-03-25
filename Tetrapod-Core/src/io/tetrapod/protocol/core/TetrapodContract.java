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
   
   public void addRequests(StructureFactory factory, int dynamicId) {
      factory.add(dynamicId, AddWebRoutesRequest.STRUCT_ID, AddWebRoutesRequest.getInstanceFactory());
      factory.add(dynamicId, PublishRequest.STRUCT_ID, PublishRequest.getInstanceFactory());
      factory.add(dynamicId, RegisterRequest.STRUCT_ID, RegisterRequest.getInstanceFactory());
      factory.add(dynamicId, RegistrySubscribeRequest.STRUCT_ID, RegistrySubscribeRequest.getInstanceFactory());
      factory.add(dynamicId, ServiceStatusUpdateRequest.STRUCT_ID, ServiceStatusUpdateRequest.getInstanceFactory());
   }
   
   public void addResponses(StructureFactory factory, int dynamicId) {
      factory.add(dynamicId, PublishResponse.STRUCT_ID, PublishResponse.getInstanceFactory());
      factory.add(dynamicId, RegisterResponse.STRUCT_ID, RegisterResponse.getInstanceFactory());
   }
   
   public void addMessages(StructureFactory factory, int dynamicId) {
      factory.add(dynamicId, EntityRegisteredMessage.STRUCT_ID, EntityRegisteredMessage.getInstanceFactory());
      factory.add(dynamicId, EntityUnregisteredMessage.STRUCT_ID, EntityUnregisteredMessage.getInstanceFactory());
      factory.add(dynamicId, EntityUpdatedMessage.STRUCT_ID, EntityUpdatedMessage.getInstanceFactory());
      factory.add(dynamicId, ServiceAddedMessage.STRUCT_ID, ServiceAddedMessage.getInstanceFactory());
      factory.add(dynamicId, ServiceRemovedMessage.STRUCT_ID, ServiceRemovedMessage.getInstanceFactory());
      factory.add(dynamicId, ServiceStatsMessage.STRUCT_ID, ServiceStatsMessage.getInstanceFactory());
      factory.add(dynamicId, ServiceUpdatedMessage.STRUCT_ID, ServiceUpdatedMessage.getInstanceFactory());
      factory.add(dynamicId, TopicPublishedMessage.STRUCT_ID, TopicPublishedMessage.getInstanceFactory());
      factory.add(dynamicId, TopicSubscribedMessage.STRUCT_ID, TopicSubscribedMessage.getInstanceFactory());
      factory.add(dynamicId, TopicUnpublishedMessage.STRUCT_ID, TopicUnpublishedMessage.getInstanceFactory());
      factory.add(dynamicId, TopicUnsubscribedMessage.STRUCT_ID, TopicUnsubscribedMessage.getInstanceFactory());
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
         factory.add(dynamicId, EntityRegisteredMessage.STRUCT_ID, EntityRegisteredMessage.getInstanceFactory());
         factory.add(dynamicId, EntityUnregisteredMessage.STRUCT_ID, EntityUnregisteredMessage.getInstanceFactory());
         factory.add(dynamicId, EntityUpdatedMessage.STRUCT_ID, EntityUpdatedMessage.getInstanceFactory());
         factory.add(dynamicId, TopicPublishedMessage.STRUCT_ID, TopicPublishedMessage.getInstanceFactory());
         factory.add(dynamicId, TopicSubscribedMessage.STRUCT_ID, TopicSubscribedMessage.getInstanceFactory());
         factory.add(dynamicId, TopicUnpublishedMessage.STRUCT_ID, TopicUnpublishedMessage.getInstanceFactory());
         factory.add(dynamicId, TopicUnsubscribedMessage.STRUCT_ID, TopicUnsubscribedMessage.getInstanceFactory());
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
         factory.add(dynamicId, ServiceAddedMessage.STRUCT_ID, ServiceAddedMessage.getInstanceFactory());
         factory.add(dynamicId, ServiceRemovedMessage.STRUCT_ID, ServiceRemovedMessage.getInstanceFactory());
         factory.add(dynamicId, ServiceStatsMessage.STRUCT_ID, ServiceStatsMessage.getInstanceFactory());
         factory.add(dynamicId, ServiceUpdatedMessage.STRUCT_ID, ServiceUpdatedMessage.getInstanceFactory());
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
