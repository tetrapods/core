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
   public static final int CONTRACT_ID = 22;
   public static final int SUB_CONTRACT_ID = 1;

   public static interface API extends APIHandler
      , ClientSessionsRequest.Handler
      , CloseClientConnectionRequest.Handler
      , GetClientInfoRequest.Handler
      , KeepAliveRequest.Handler
      , RegisterRequest.Handler
      , SetAlternateIdRequest.Handler
      {}
   
   private volatile Structure[] requests = null;

   public Structure[] getRequests() {
      if (requests == null) {
         synchronized(this) {
            if (requests == null) {
               requests = new Structure[] {
                  new KeepAliveRequest(),
                  new RegisterRequest(),
                  new SetAlternateIdRequest(),
                  new GetClientInfoRequest(),
                  new CloseClientConnectionRequest(),
                  new ClientSessionsRequest(),
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
                  new RegisterResponse(),
                  new GetClientInfoResponse(),
                  new ClientSessionsResponse(),
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
                  
               };
            }
         }
      }
      return structs;
   }
   
   public String getName() {
      return WebContract.NAME;
   } 
   
   public int getContractId() {
      return WebContract.CONTRACT_ID;
   }
   
   public int getSubContractId() {
      return WebContract.SUB_CONTRACT_ID;
   }

   public int getContractVersion() {
      return WebContract.VERSION;
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

   public static final int ERROR_UNKNOWN_ALT_ID = 5866283; 
   public static final int ERROR_UNKNOWN_CLIENT_ID = 5653403; 
}
