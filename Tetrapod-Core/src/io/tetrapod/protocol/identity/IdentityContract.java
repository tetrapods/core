package  io.tetrapod.protocol.identity;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;

/**
 * The base contract every service must support
 */

@SuppressWarnings("unused")
public class IdentityContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "Identity";
   
   /**
    * hardcoded contract id, < 20 is reserved for core services
    */
   public static final int CONTRACT_ID = 2; 
   
   public static interface API extends
      CreateRequest.Handler,
      InfoRequest.Handler,
      LoginRequest.Handler
      {}
   
   public void addRequests(StructureFactory factory, int dynamicId) {
      factory.add(dynamicId, CreateRequest.STRUCT_ID, CreateRequest.getInstanceFactory());
      factory.add(dynamicId, InfoRequest.STRUCT_ID, InfoRequest.getInstanceFactory());
      factory.add(dynamicId, LoginRequest.STRUCT_ID, LoginRequest.getInstanceFactory());
   }
   
   public void addResponses(StructureFactory factory, int dynamicId) {
      factory.add(dynamicId, CreateResponse.STRUCT_ID, CreateResponse.getInstanceFactory());
      factory.add(dynamicId, InfoResponse.STRUCT_ID, InfoResponse.getInstanceFactory());
      factory.add(dynamicId, LoginResponse.STRUCT_ID, LoginResponse.getInstanceFactory());
   }
   
   public void addMessages(StructureFactory factory, int dynamicId) {
      
   }
   
   public String getName() {
      return IdentityContract.NAME;
   } 

   public static final int ERROR_UNKNOWN_USERNAME = 983354; 
   public static final int ERROR_WRONG_PASSWORD = 8315566; 
}
