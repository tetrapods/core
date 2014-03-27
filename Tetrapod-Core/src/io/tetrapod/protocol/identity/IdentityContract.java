package  io.tetrapod.protocol.identity;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.WebRoute;

@SuppressWarnings("unused")
public class IdentityContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "Identity";
   public static final int CONTRACT_ID = 4;
   
   public static interface API extends
      CreateRequest.Handler,
      InfoRequest.Handler,
      LoginRequest.Handler,
      UpdatePropertiesRequest.Handler
      {}
   
   public Structure[] getRequests() {
      return new Structure[] {
         new LoginRequest(),
         new CreateRequest(),
         new InfoRequest(),
         new UpdatePropertiesRequest(),
      };
   }
   
   public Structure[] getResponses() {
      return new Structure[] {
         new LoginResponse(),
         new CreateResponse(),
         new InfoResponse(),
      };
   }
   
   public Structure[] getMessages() {
      return new Structure[] {
         
      };
   }
   
   public Structure[] getStructs() {
      return new Structure[] {
         new User(),
      };
   }
   
   public String getName() {
      return IdentityContract.NAME;
   } 
   
   public int getContractId() {
      return IdentityContract.CONTRACT_ID;
   }
   
   public WebRoute[] getWebRoutes() {
      return new WebRoute[] {
         new WebRoute("/api/identity/login", LoginRequest.STRUCT_ID, IdentityContract.CONTRACT_ID),
         new WebRoute("/api/identity/updateProperties", UpdatePropertiesRequest.STRUCT_ID, IdentityContract.CONTRACT_ID),
      };
   }

   public static final int ERROR_UNKNOWN_USERNAME = 983354; 
   public static final int ERROR_WRONG_PASSWORD = 8315566; 
}
