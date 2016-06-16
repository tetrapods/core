package  io.tetrapod.protocol.web;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.WebRoute;

@SuppressWarnings("all")
public class WebContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "Web";
   public static final int CONTRACT_ID = 20;
   
   public static interface API extends APIHandler
      , ClientSessionsRequest.Handler
      , CloseClientConnectionRequest.Handler
      , GetClientInfoRequest.Handler
      , KeepAliveRequest.Handler
      , RegisterRequest.Handler
      , SetAlternateIdRequest.Handler
      {}
   
   public Structure[] getRequests() {
      return new Structure[] {
         new KeepAliveRequest(),
         new RegisterRequest(),
         new SetAlternateIdRequest(),
         new GetClientInfoRequest(),
         new CloseClientConnectionRequest(),
         new ClientSessionsRequest(),
      };
   }
   
   public Structure[] getResponses() {
      return new Structure[] {
         new RegisterResponse(),
         new GetClientInfoResponse(),
         new ClientSessionsResponse(),
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

   public static final int ERROR_UNKNOWN_ALT_ID = 5866283; 
   public static final int ERROR_UNKNOWN_CLIENT_ID = 5653403; 
}
