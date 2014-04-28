package  io.tetrapod.protocol.service;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.WebRoute;

/**
 * The base contract every service must support
 */

@SuppressWarnings("unused")
public class BaseServiceContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "BaseService";
   public static final int CONTRACT_ID = 2;
   
   public static interface API extends
      PauseRequest.Handler,
      RestartRequest.Handler,
      ServiceDetailsRequest.Handler,
      ServiceStatsSubscribeRequest.Handler,
      ServiceStatsUnsubscribeRequest.Handler,
      ShutdownRequest.Handler,
      UnpauseRequest.Handler
      {}
   
   public Structure[] getRequests() {
      return new Structure[] {
         new PauseRequest(),
         new UnpauseRequest(),
         new ShutdownRequest(),
         new RestartRequest(),
         new ServiceDetailsRequest(),
         new ServiceStatsSubscribeRequest(),
         new ServiceStatsUnsubscribeRequest(),
      };
   }
   
   public Structure[] getResponses() {
      return new Structure[] {
         new ServiceDetailsResponse(),
      };
   }
   
   public Structure[] getMessages() {
      return new Structure[] {
         new ServiceStatsMessage(),
      };
   }
   
   public Structure[] getStructs() {
      return new Structure[] {
         new ServiceCommand(),
      };
   }
   
   public String getName() {
      return BaseServiceContract.NAME;
   } 
   
   public int getContractId() {
      return BaseServiceContract.CONTRACT_ID;
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
         return BaseServiceContract.NAME;
      }
      
      public int getContractId() {
         return BaseServiceContract.CONTRACT_ID;
      } 
       
   }
      
}
