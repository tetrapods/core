package  io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.WebRoute;

/**
 * The core tetrapod service
 */

@SuppressWarnings("unused")
public class CoreContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "Core";
   public static final int CONTRACT_ID = 1;
   
   public static interface API extends APIHandler
      , DirectConnectionRequest.Handler
      , DummyRequest.Handler
      , PauseRequest.Handler
      , RestartRequest.Handler
      , ServiceDetailsRequest.Handler
      , ServiceLogsRequest.Handler
      , ServiceStatsSubscribeRequest.Handler
      , ServiceStatsUnsubscribeRequest.Handler
      , ShutdownRequest.Handler
      , UnpauseRequest.Handler
      , ValidateConnectionRequest.Handler
      , WebAPIRequest.Handler
      {}
   
   public Structure[] getRequests() {
      return new Structure[] {
         new PauseRequest(),
         new UnpauseRequest(),
         new ShutdownRequest(),
         new RestartRequest(),
         new ServiceStatsSubscribeRequest(),
         new ServiceStatsUnsubscribeRequest(),
         new ServiceDetailsRequest(),
         new ServiceLogsRequest(),
         new WebAPIRequest(),
         new DirectConnectionRequest(),
         new ValidateConnectionRequest(),
         new DummyRequest(),
      };
   }
   
   public Structure[] getResponses() {
      return new Structure[] {
         new ServiceDetailsResponse(),
         new ServiceLogsResponse(),
         new WebAPIResponse(),
         new DirectConnectionResponse(),
         new ValidateConnectionResponse(),
      };
   }
   
   public Structure[] getMessages() {
      return new Structure[] {
         new ServiceStatsMessage(),
      };
   }
   
   public Structure[] getStructs() {
      return new Structure[] {
         new Core(),
         new RequestHeader(),
         new ResponseHeader(),
         new MessageHeader(),
         new ServiceCommand(),
         new ServerAddress(),
         new Subscriber(),
         new WebRoute(),
         new TypeDescriptor(),
         new StructDescription(),
         new ServiceLogEntry(),
      };
   }
   
   public String getName() {
      return CoreContract.NAME;
   } 
   
   public int getContractId() {
      return CoreContract.CONTRACT_ID;
   }
   
   public WebRoute[] getWebRoutes() {
      return new WebRoute[] {
         
      };
   }

   public static class ServiceStats extends Contract {
      public static interface API extends
         ServiceStatsMessage.Handler
         {}
         
      public Structure[] getMessages() {
         return new Structure[] {
            new ServiceStatsMessage(),
         };
      }
      
      public String getName() {
         return CoreContract.NAME;
      }
      
      public int getContractId() {
         return CoreContract.CONTRACT_ID;
      } 
       
   }
      
   /**
    * Request's session was disconnected
    */
   public static final int ERROR_CONNECTION_CLOSED = 7; 
   
   /**
    * client has sent too many requests recently
    */
   public static final int ERROR_FLOOD = 12; 
   
   /**
    * An addressed entityId was invalid
    */
   public static final int ERROR_INVALID_ENTITY = 9; 
   
   /**
    * Caller does not have sufficient rights to call this Request
    */
   public static final int ERROR_INVALID_RIGHTS = 8; 
   
   /**
    * for any sort of invalid token
    */
   public static final int ERROR_INVALID_TOKEN = 13; 
   public static final int ERROR_NOT_CONFIGURED = 2718243; 
   
   /**
    * Protocol versions are not compatible
    */
   public static final int ERROR_PROTOCOL_MISMATCH = 5; 
   
   /**
    * rights token has expired, need to login again
    */
   public static final int ERROR_RIGHTS_EXPIRED = 10; 
   
   /**
    * Unable to deserialize the request
    */
   public static final int ERROR_SERIALIZATION = 4; 
   
   /**
    * This service is over capacity, so the request was not performed
    */
   public static final int ERROR_SERVICE_OVERLOADED = 11; 
   
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
    * Service exists and received request, but doesn't know how to handle it
    */
   public static final int ERROR_UNKNOWN_REQUEST = 6; 
}
