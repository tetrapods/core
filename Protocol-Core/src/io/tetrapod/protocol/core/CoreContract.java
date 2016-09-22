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

@SuppressWarnings("all")
public class CoreContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "Core";
   public static final int CONTRACT_ID = 1;
   public static final int SUB_CONTRACT_ID = 1;

   public static interface API extends APIHandler
      , DebugRequest.Handler
      , DirectConnectionRequest.Handler
      , DummyRequest.Handler
      , HostInfoRequest.Handler
      , HostStatsRequest.Handler
      , InternalShutdownRequest.Handler
      , PauseRequest.Handler
      , PurgeRequest.Handler
      , RebalanceRequest.Handler
      , ReleaseExcessRequest.Handler
      , ResetServiceErrorLogsRequest.Handler
      , RestartRequest.Handler
      , ServiceDetailsRequest.Handler
      , ServiceErrorLogsRequest.Handler
      , ServiceLogsRequest.Handler
      , ServiceRequestStatsRequest.Handler
      , ServiceStatsSubscribeRequest.Handler
      , ServiceStatsUnsubscribeRequest.Handler
      , SetCommsLogLevelRequest.Handler
      , ShutdownRequest.Handler
      , UnpauseRequest.Handler
      , ValidateConnectionRequest.Handler
      , WebAPIRequest.Handler
      {}
   
   private volatile Structure[] requests = null;

   public Structure[] getRequests() {
      if (requests == null) {
         synchronized(this) {
            if (requests == null) {
               requests = new Structure[] {
                  new PauseRequest(),
                  new UnpauseRequest(),
                  new RebalanceRequest(),
                  new ReleaseExcessRequest(),
                  new PurgeRequest(),
                  new InternalShutdownRequest(),
                  new ShutdownRequest(),
                  new RestartRequest(),
                  new ServiceStatsSubscribeRequest(),
                  new ServiceStatsUnsubscribeRequest(),
                  new ServiceDetailsRequest(),
                  new ServiceLogsRequest(),
                  new ServiceRequestStatsRequest(),
                  new HostInfoRequest(),
                  new HostStatsRequest(),
                  new ServiceErrorLogsRequest(),
                  new ResetServiceErrorLogsRequest(),
                  new SetCommsLogLevelRequest(),
                  new DebugRequest(),
                  new WebAPIRequest(),
                  new DirectConnectionRequest(),
                  new ValidateConnectionRequest(),
                  new DummyRequest(),
               };
            }
         }
      }
      return requests;
   }
   
   private volatile Structure[] responses = null;

   public Structure[] getResponses() {
      if (responses == null) {
         synchronized(this) {
            if (responses == null) {
               responses = new Structure[] {
                  new ServiceDetailsResponse(),
                  new ServiceLogsResponse(),
                  new ServiceRequestStatsResponse(),
                  new HostInfoResponse(),
                  new HostStatsResponse(),
                  new ServiceErrorLogsResponse(),
                  new WebAPIResponse(),
                  new DirectConnectionResponse(),
                  new ValidateConnectionResponse(),
               };
            }
         }
      }
      return responses;
   }
   
   private volatile Structure[] messages = null;

   public Structure[] getMessages() {
      if (messages == null) {
         synchronized(this) {
            if (messages == null) {
               messages = new Structure[] {
                  new ServiceStatsMessage(),
               };
            }
         }
      }
      return messages;
   }
   
   private volatile Structure[] structs = null;

   public Structure[] getStructs() {
      if (structs == null) {
         synchronized(this) {
            if (structs == null) {
               structs = new Structure[] {
                  new Core(),
                  new RequestHeader(),
                  new ResponseHeader(),
                  new MessageHeader(),
                  new CommsLogFileHeader(),
                  new CommsLogHeader(),
                  new MissingStructDef(),
                  new ServiceCommand(),
                  new ServerAddress(),
                  new Admin(),
                  new StatPair(),
                  new RequestStat(),
                  new WebRoute(),
                  new TypeDescriptor(),
                  new ContractDescription(),
                  new StructDescription(),
                  new ServiceLogEntry(),
               };
            }
         }
      }
      return structs;
   }
   
   public String getName() {
      return CoreContract.NAME;
   } 
   
   public int getContractId() {
      return CoreContract.CONTRACT_ID;
   }
   
   public int getSubContractId() {
      return CoreContract.SUB_CONTRACT_ID;
   }

   public int getContractVersion() {
      return CoreContract.VERSION;
   }

   private volatile WebRoute[] webRoutes = null;

   public WebRoute[] getWebRoutes() {
      if (webRoutes == null) {
         synchronized(this) {
            webRoutes = new WebRoute[] {
               
            };
         }
      }
      return webRoutes;
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
       
      public int getSubContractId() {
         return CoreContract.SUB_CONTRACT_ID;
      }
   
      public int getContractVersion() {
         return CoreContract.VERSION;
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
    * for any sort of invalid data
    */
   public static final int ERROR_INVALID_DATA = 15; 
   
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
    * for any sort of generic security violation
    */
   public static final int ERROR_SECURITY = 16; 
   
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
   
   /**
    * request is currently unsupported
    */
   public static final int ERROR_UNSUPPORTED = 14; 
}
