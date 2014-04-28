package  io.tetrapod.protocol.sample;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.WebRoute;

@SuppressWarnings("unused")
public class SampleContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "Sample";
   public static final int CONTRACT_ID = 999999;
   
   public static final int GLOBAL_CONST = 42; 
   public static final int ANOTHER_GLOBAL_CONST = 43; 
   
   public static interface API extends
      AnotherTestRequest.Handler,
      TestRequest.Handler,
      ThirdTestRequest.Handler
      {}
   
   public Structure[] getRequests() {
      return new Structure[] {
         new TestRequest(),
         new AnotherTestRequest(),
         new ThirdTestRequest(),
      };
   }
   
   public Structure[] getResponses() {
      return new Structure[] {
         new TestResponse(),
      };
   }
   
   public Structure[] getMessages() {
      return new Structure[] {
         new StatusUpdateMessage(),
         new OtherUpdateMessage(),
         new NonSubMessage(),
      };
   }
   
   public Structure[] getStructs() {
      return new Structure[] {
         new MissingOne(),
         new TestInfo(),
      };
   }
   
   public String getName() {
      return SampleContract.NAME;
   } 
   
   public int getContractId() {
      return SampleContract.CONTRACT_ID;
   }
   
   public WebRoute[] getWebRoutes() {
      return new WebRoute[] {
         
      };
   }

   public static class MySub extends Contract {
      public static interface API extends
         OtherUpdateMessage.Handler,
         StatusUpdateMessage.Handler
         {}
         
      public Structure[] getMessages() {
         return new Structure[] {
            new OtherUpdateMessage(),
            new StatusUpdateMessage(),
         };
      }
      
      public String getName() {
         return SampleContract.NAME;
      }
      
      public int getContractId() {
         return SampleContract.CONTRACT_ID;
      } 
       
   }
      
   public static final int ERROR_AAA = 8448998; 
   public static final int ERROR_BBB = 7649641; 
   public static final int ERROR_CC = 14291463; 
   public static final int ERROR_NOT_ENOUGH_LAME = 9828797; 
   
   /**
    * genned comments don't attach to errors alas
    */
   public static final int ERROR_TOO_MUCH_AWESOME = 6313007; 
}
