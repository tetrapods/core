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
   public static final int CONTRACT_ID = 15;
   
   public static interface API extends APIHandler
      
      {}
   
   private volatile Structure[] requests = null;

   public Structure[] getRequests() {
      if (requests == null) {
         synchronized(this) {
            requests = new Structure[] {
               
            };
         }
      }
      return requests;
   }
   
   private volatile Structure[] responses = null;

   public Structure[] getResponses() {
      if (responses == null) {
         synchronized(this) {
            responses = new Structure[] {
               
            };
         }
      }
      return responses;
   }
   
   private volatile Structure[] messages = null;

   public Structure[] getMessages() {
      if (messages == null) {
         synchronized(this) {
            messages = new Structure[] {
               
            };
         }
      }
      return messages;
   }
   
   private volatile Structure[] structs = null;

   public Structure[] getStructs() {
      if (structs == null) {
         synchronized(this) {
            structs = new Structure[] {
               
            };
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

}
