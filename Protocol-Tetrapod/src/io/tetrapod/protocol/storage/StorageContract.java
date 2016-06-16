package  io.tetrapod.protocol.storage;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.WebRoute;

@SuppressWarnings("all")
public class StorageContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "Storage";
   public static final int CONTRACT_ID = 3;
   
   public static interface API extends APIHandler
      , StorageDeleteRequest.Handler
      , StorageGetRequest.Handler
      , StorageSetRequest.Handler
      {}
   
   private volatile Structure[] requests = null;

   public Structure[] getRequests() {
      if (requests == null) {
         synchronized(this) {
            requests = new Structure[] {
               new StorageSetRequest(),
               new StorageGetRequest(),
               new StorageDeleteRequest(),
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
               new StorageGetResponse(),
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
      return StorageContract.NAME;
   } 
   
   public int getContractId() {
      return StorageContract.CONTRACT_ID;
   }
   
   public int getContractVersion() {
      return StorageContract.VERSION;
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
