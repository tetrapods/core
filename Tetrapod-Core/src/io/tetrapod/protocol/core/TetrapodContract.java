package  io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;

/**
 * The core tetrapod service
 */

@SuppressWarnings("unused")
public class TetrapodContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "Tetrapod";
   public static final int CONTRACT_ID = 1;
   
   public static interface API extends
      PublishRequest.Handler,
      RegisterRequest.Handler,
      RegistrySubscribeRequest.Handler
      {}
   
   public void addRequests(StructureFactory factory, int dynamicId) {
      factory.add(dynamicId, PublishRequest.STRUCT_ID, PublishRequest.getInstanceFactory());
      factory.add(dynamicId, RegisterRequest.STRUCT_ID, RegisterRequest.getInstanceFactory());
      factory.add(dynamicId, RegistrySubscribeRequest.STRUCT_ID, RegistrySubscribeRequest.getInstanceFactory());
   }
   
   public void addResponses(StructureFactory factory, int dynamicId) {
      factory.add(dynamicId, PublishResponse.STRUCT_ID, PublishResponse.getInstanceFactory());
      factory.add(dynamicId, RegisterResponse.STRUCT_ID, RegisterResponse.getInstanceFactory());
   }
   
   public void addMessages(StructureFactory factory, int dynamicId) {
      factory.add(dynamicId, EntityRegisteredMessage.STRUCT_ID, EntityRegisteredMessage.getInstanceFactory());
      factory.add(dynamicId, EntityUnregisteredMessage.STRUCT_ID, EntityUnregisteredMessage.getInstanceFactory());
      factory.add(dynamicId, ServiceAddedMessage.STRUCT_ID, ServiceAddedMessage.getInstanceFactory());
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

   public static class Registry extends Contract {
      public static interface API extends
         EntityRegisteredMessage.Handler,
         EntityUnregisteredMessage.Handler,
         TopicPublishedMessage.Handler,
         TopicSubscribedMessage.Handler,
         TopicUnpublishedMessage.Handler,
         TopicUnsubscribedMessage.Handler
         {}
         
      public void addMessages(StructureFactory factory, int dynamicId) {
         factory.add(dynamicId, EntityRegisteredMessage.STRUCT_ID, EntityRegisteredMessage.getInstanceFactory());
         factory.add(dynamicId, EntityUnregisteredMessage.STRUCT_ID, EntityUnregisteredMessage.getInstanceFactory());
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
      public static class ServiceInfo extends Contract {
      public static interface API extends
         ServiceAddedMessage.Handler
         {}
         
      public void addMessages(StructureFactory factory, int dynamicId) {
         factory.add(dynamicId, ServiceAddedMessage.STRUCT_ID, ServiceAddedMessage.getInstanceFactory());
      }
      
      public String getName() {
         return TetrapodContract.NAME;
      }
      
      public int getContractId() {
         return TetrapodContract.CONTRACT_ID;
      } 
       
   }
      
   /**
    * Caller does not have sufficient rights to call this Request
    */
   public static final int ERROR_INVALID_RIGHTS = 7; 
   
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
