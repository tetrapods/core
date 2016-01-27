package  io.tetrapod.protocol.web;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.WebRoute;

@SuppressWarnings("unused")
public class WebContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "Web";
   public static final int CONTRACT_ID = 20;
   
   public static interface API extends APIHandler
      , KeepAliveRequest.Handler
      , RegisterRequest.Handler
      {}
   
   public Structure[] getRequests() {
      return new Structure[] {
         new RegisterRequest(),
         new KeepAliveRequest(),
      };
   }
   
   public Structure[] getResponses() {
      return new Structure[] {
         new RegisterResponse(),
      };
   }
   
   public Structure[] getMessages() {
      return new Structure[] {
         
      };
   }
   
   public Structure[] getStructs() {
      return new Structure[] {
         
      };
   }
   
   public String getName() {
      return WebContract.NAME;
   } 
   
   public int getContractId() {
      return WebContract.CONTRACT_ID;
   }
   
   public int getContractVersion() {
      return WebContract.VERSION;
   }
   
   public WebRoute[] getWebRoutes() {
      return new WebRoute[] {
         
      };
   }

}
