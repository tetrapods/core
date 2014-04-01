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
      GetAuthSecretRequest.Handler,
      InfoRequest.Handler,
      LinkRequest.Handler,
      LoginRequest.Handler,
      LogoutRequest.Handler,
      ModifyIdentityRequest.Handler,
      UpdatePropertiesRequest.Handler
      {}
   
   public Structure[] getRequests() {
      return new Structure[] {
         new LoginRequest(),
         new LogoutRequest(),
         new ModifyIdentityRequest(),
         new CreateRequest(),
         new LinkRequest(),
         new InfoRequest(),
         new UpdatePropertiesRequest(),
         new GetAuthSecretRequest(),
      };
   }
   
   public Structure[] getResponses() {
      return new Structure[] {
         new LoginResponse(),
         new CreateResponse(),
         new LinkResponse(),
         new InfoResponse(),
         new GetAuthSecretResponse(),
      };
   }
   
   public Structure[] getMessages() {
      return new Structure[] {
         
      };
   }
   
   public Structure[] getStructs() {
      return new Structure[] {
         new Identity(),
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
         
      };
   }

   public static final int ERROR_IDENTITY_TAKEN = 5562311; 
   public static final int ERROR_INVALID_INPUT = 9895911; 
   public static final int ERROR_UNKNOWN_USERNAME = 983354; 
   public static final int ERROR_UNMODIFIABLE_IDENTITY = 548527; 
   public static final int ERROR_VERIFICATION_ERROR = 10526271; 
   public static final int ERROR_VERIFICATION_FAILURE = 3531687; 
}
