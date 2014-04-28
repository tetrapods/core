package  io.tetrapod.protocol.storage;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.WebRoute;

@SuppressWarnings("unused")
public class StorageContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "Storage";
   public static final int CONTRACT_ID = 3;
   
   public static interface API extends
      StorageDeleteRequest.Handler,
      StorageGetRequest.Handler,
      StorageSetRequest.Handler
      {}
   
   public Structure[] getRequests() {
      return new Structure[] {
         new StorageSetRequest(),
         new StorageGetRequest(),
         new StorageDeleteRequest(),
      };
   }
   
   public Structure[] getResponses() {
      return new Structure[] {
         new StorageGetResponse(),
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
      return StorageContract.NAME;
   } 
   
   public int getContractId() {
      return StorageContract.CONTRACT_ID;
   }
   
   public WebRoute[] getWebRoutes() {
      return new WebRoute[] {
         
      };
   }

}
