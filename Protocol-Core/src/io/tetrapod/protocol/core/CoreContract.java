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
      , PauseRequest.Handler
      , RestartRequest.Handler
      , ServiceDetailsRequest.Handler
      , ServiceStatsSubscribeRequest.Handler
      , ServiceStatsUnsubscribeRequest.Handler
      , ShutdownRequest.Handler
      , UnpauseRequest.Handler
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
      };
   }
   
   public Structure[] getResponses() {
      return new Structure[] {
         new ServiceDetailsResponse(),
      };
   }
   
   public Structure[] getMessages() {
      return new Structure[] {
         
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

   /**
    * Request's session was disconnected
    */
   public static final int ERROR_CONNECTION_CLOSED = 7; 
   
   /**
    * An addressed entityId was invalid
    */
   public static final int ERROR_INVALID_ENTITY = 9; 
   
   /**
    * Caller does not have sufficient rights to call this Request
    */
   public static final int ERROR_INVALID_RIGHTS = 8; 
   
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
