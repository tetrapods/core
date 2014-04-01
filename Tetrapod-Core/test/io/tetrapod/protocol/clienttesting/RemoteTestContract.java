package  io.tetrapod.protocol.clienttesting;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.WebRoute;

@SuppressWarnings("unused")
public class RemoteTestContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "RemoteTest";
   public static final int CONTRACT_ID = 5;
   
   public static interface API extends
      MeaningOfLifeTheUniversAndEverythingRequest.Handler
      {}
   
   public Structure[] getRequests() {
      return new Structure[] {
         new MeaningOfLifeTheUniversAndEverythingRequest(),
      };
   }
   
   public Structure[] getResponses() {
      return new Structure[] {
         new MeaningOfLifeTheUniversAndEverythingResponse(),
      };
   }
   
   public Structure[] getMessages() {
      return new Structure[] {
         new DatatypeTestMessage(),
      };
   }
   
   public Structure[] getStructs() {
      return new Structure[] {
         
      };
   }
   
   public String getName() {
      return RemoteTestContract.NAME;
   } 
   
   public int getContractId() {
      return RemoteTestContract.CONTRACT_ID;
   }
   
   public WebRoute[] getWebRoutes() {
      return new WebRoute[] {
         
      };
   }

   public static final int ERROR_MISSING_GUESS = 8254177; 
}
