package  io.tetrapod.protocol.service;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;
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
      ShutdownRequest.Handler,
      UnpauseRequest.Handler
      {}
   
   public void addRequests(StructureFactory factory) {
      factory.add(new PauseRequest());
      factory.add(new RestartRequest());
      factory.add(new ShutdownRequest());
      factory.add(new UnpauseRequest());
   }
   
   public void addResponses(StructureFactory factory) {
      
   }
   
   public void addMessages(StructureFactory factory) {
      
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

}
