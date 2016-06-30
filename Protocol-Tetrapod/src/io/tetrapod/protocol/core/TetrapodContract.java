package  io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.WebRoute;

/**
 * The core tetrapod service
 */

@SuppressWarnings("all")
public class TetrapodContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "Tetrapod";
   public static final int CONTRACT_ID = 1;
   public static final int SUB_CONTRACT_ID = 1;

   /**
    * Supports up to 2047 tetrapods
    */
   public static final int MAX_PARENTS = 0x000007FF; 
   
   /**
    * Supports up to 1048575 entities per tetrapod
    */
   public static final int MAX_ID = 0x000FFFFF; 
   
   /**
    * bits to shift to get parentId
    */
   public static final int PARENT_ID_SHIFT = 20; 
   
   /**
    * mask to get parentId
    */
   public static final int PARENT_ID_MASK = 0x7FF00000; 
   
   public static interface API extends APIHandler
      , AddServiceInformationRequest.Handler
      , AdminAuthorizeRequest.Handler
      , AdminChangePasswordRequest.Handler
      , AdminChangeRightsRequest.Handler
      , AdminCreateRequest.Handler
      , AdminDeleteRequest.Handler
      , AdminLoginRequest.Handler
      , AdminResetPasswordRequest.Handler
      , AdminSessionTokenRequest.Handler
      , AdminSubscribeRequest.Handler
      , ClaimOwnershipRequest.Handler
      , ClusterJoinRequest.Handler
      , DelClusterPropertyRequest.Handler
      , DelWebRootRequest.Handler
      , ExecuteBuildCommandRequest.Handler
      , GetServiceBuildInfoRequest.Handler
      , InternalSetClusterPropertyRequest.Handler
      , LockRequest.Handler
      , LogRegistryStatsRequest.Handler
      , NagiosStatusRequest.Handler
      , RaftLeaderRequest.Handler
      , RaftStatsRequest.Handler
      , RegisterRequest.Handler
      , ReleaseOwnershipRequest.Handler
      , RetainOwnershipRequest.Handler
      , ServiceStatusUpdateRequest.Handler
      , ServicesSubscribeRequest.Handler
      , ServicesUnsubscribeRequest.Handler
      , SetClusterPropertyRequest.Handler
      , SetWebRootRequest.Handler
      , SnapshotRequest.Handler
      , SubscribeOwnershipRequest.Handler
      , UnlockRequest.Handler
      , UnregisterRequest.Handler
      , UnsubscribeOwnershipRequest.Handler
      {}
   
   private volatile Structure[] requests = null;

   public Structure[] getRequests() {
      if (requests == null) {
         synchronized(this) {
            if (requests == null) {
               requests = new Structure[] {
                  new RegisterRequest(),
                  new ClusterJoinRequest(),
                  new UnregisterRequest(),
                  new ServicesSubscribeRequest(),
                  new ServicesUnsubscribeRequest(),
                  new ServiceStatusUpdateRequest(),
                  new AddServiceInformationRequest(),
                  new LogRegistryStatsRequest(),
                  new AdminLoginRequest(),
                  new AdminAuthorizeRequest(),
                  new AdminSessionTokenRequest(),
                  new AdminCreateRequest(),
                  new AdminDeleteRequest(),
                  new AdminChangePasswordRequest(),
                  new AdminResetPasswordRequest(),
                  new AdminChangeRightsRequest(),
                  new GetServiceBuildInfoRequest(),
                  new ExecuteBuildCommandRequest(),
                  new RaftLeaderRequest(),
                  new RaftStatsRequest(),
                  new AdminSubscribeRequest(),
                  new InternalSetClusterPropertyRequest(),
                  new SetClusterPropertyRequest(),
                  new DelClusterPropertyRequest(),
                  new SetWebRootRequest(),
                  new DelWebRootRequest(),
                  new LockRequest(),
                  new UnlockRequest(),
                  new SnapshotRequest(),
                  new ClaimOwnershipRequest(),
                  new RetainOwnershipRequest(),
                  new ReleaseOwnershipRequest(),
                  new SubscribeOwnershipRequest(),
                  new UnsubscribeOwnershipRequest(),
                  new NagiosStatusRequest(),
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
                  new AdminLoginResponse(),
                  new AdminAuthorizeResponse(),
                  new AdminSessionTokenResponse(),
                  new GetServiceBuildInfoResponse(),
                  new RaftLeaderResponse(),
                  new RaftStatsResponse(),
                  new LockResponse(),
                  new ClaimOwnershipResponse(),
                  new NagiosStatusResponse(),
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
                  new EntityMessage(),
                  new TopicPublishedMessage(),
                  new TopicUnpublishedMessage(),
                  new TopicSubscribedMessage(),
                  new TopicUnsubscribedMessage(),
                  new TopicNotFoundMessage(),
                  new SubscriberNotFoundMessage(),
                  new BuildCommandProgressMessage(),
                  new ServiceAddedMessage(),
                  new ServiceRemovedMessage(),
                  new ServiceUpdatedMessage(),
                  new ClusterPropertyAddedMessage(),
                  new ClusterPropertyRemovedMessage(),
                  new ClusterSyncedMessage(),
                  new RegisterContractMessage(),
                  new ClusterMemberMessage(),
                  new WebRootAddedMessage(),
                  new WebRootRemovedMessage(),
                  new AdminUserAddedMessage(),
                  new AdminUserRemovedMessage(),
                  new ClaimOwnershipMessage(),
                  new RetainOwnershipMessage(),
                  new ReleaseOwnershipMessage(),
                  new NagiosStatusMessage(),
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
                  new Entity(),
                  new BuildInfo(),
                  new BuildCommand(),
                  new ClusterProperty(),
                  new WebRootDef(),
                  new Owner(),
               };
            }
         }
      }
      return structs;
   }
   
   public String getName() {
      return TetrapodContract.NAME;
   } 
   
   public int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }
   
   public int getSubContractId() {
      return TetrapodContract.SUB_CONTRACT_ID;
   }

   public int getContractVersion() {
      return TetrapodContract.VERSION;
   }

   private volatile WebRoute[] webRoutes = null;

   public WebRoute[] getWebRoutes() {
      if (webRoutes == null) {
         synchronized(this) {
            webRoutes = new WebRoute[] {
               new WebRoute("/api/admin_login", AdminLoginRequest.STRUCT_ID, TetrapodContract.CONTRACT_ID),
               new WebRoute("/api/admin_session_token", AdminSessionTokenRequest.STRUCT_ID, TetrapodContract.CONTRACT_ID),
               new WebRoute("/api/set_web_root", SetWebRootRequest.STRUCT_ID, TetrapodContract.CONTRACT_ID),
            };
         }
      }
      return webRoutes;
   }

   public static class Cluster extends Contract {
      public static interface API extends
         ClusterMemberMessage.Handler,
         ClusterPropertyAddedMessage.Handler,
         ClusterPropertyRemovedMessage.Handler,
         ClusterSyncedMessage.Handler,
         RegisterContractMessage.Handler,
         WebRootAddedMessage.Handler,
         WebRootRemovedMessage.Handler
         {}
         
      public Structure[] getMessages() {
         return new Structure[] {
            new ClusterMemberMessage(),
            new ClusterPropertyAddedMessage(),
            new ClusterPropertyRemovedMessage(),
            new ClusterSyncedMessage(),
            new RegisterContractMessage(),
            new WebRootAddedMessage(),
            new WebRootRemovedMessage(),
         };
      }
      
      public String getName() {
         return TetrapodContract.NAME;
      }
      
      public int getContractId() {
         return TetrapodContract.CONTRACT_ID;
      } 
       
      public int getSubContractId() {
         return TetrapodContract.SUB_CONTRACT_ID;
      }
   
      public int getContractVersion() {
         return TetrapodContract.VERSION;
      } 
       
   }
   
   public static class Ownership extends Contract {
      public static interface API extends
         ClaimOwnershipMessage.Handler,
         ReleaseOwnershipMessage.Handler,
         RetainOwnershipMessage.Handler
         {}
         
      public Structure[] getMessages() {
         return new Structure[] {
            new ClaimOwnershipMessage(),
            new ReleaseOwnershipMessage(),
            new RetainOwnershipMessage(),
         };
      }
      
      public String getName() {
         return TetrapodContract.NAME;
      }
      
      public int getContractId() {
         return TetrapodContract.CONTRACT_ID;
      } 
       
      public int getSubContractId() {
         return TetrapodContract.SUB_CONTRACT_ID;
      }
   
      public int getContractVersion() {
         return TetrapodContract.VERSION;
      } 
       
   }
   
   public static class Pubsub extends Contract {
      public static interface API extends
         TopicPublishedMessage.Handler,
         TopicSubscribedMessage.Handler,
         TopicUnpublishedMessage.Handler,
         TopicUnsubscribedMessage.Handler
         {}
         
      public Structure[] getMessages() {
         return new Structure[] {
            new TopicPublishedMessage(),
            new TopicSubscribedMessage(),
            new TopicUnpublishedMessage(),
            new TopicUnsubscribedMessage(),
         };
      }
      
      public String getName() {
         return TetrapodContract.NAME;
      }
      
      public int getContractId() {
         return TetrapodContract.CONTRACT_ID;
      } 
       
      public int getSubContractId() {
         return TetrapodContract.SUB_CONTRACT_ID;
      }
   
      public int getContractVersion() {
         return TetrapodContract.VERSION;
      } 
       
   }
   
   public static class Services extends Contract {
      public static interface API extends
         ServiceAddedMessage.Handler,
         ServiceRemovedMessage.Handler,
         ServiceUpdatedMessage.Handler
         {}
         
      public Structure[] getMessages() {
         return new Structure[] {
            new ServiceAddedMessage(),
            new ServiceRemovedMessage(),
            new ServiceUpdatedMessage(),
         };
      }
      
      public String getName() {
         return TetrapodContract.NAME;
      }
      
      public int getContractId() {
         return TetrapodContract.CONTRACT_ID;
      } 
       
      public int getSubContractId() {
         return TetrapodContract.SUB_CONTRACT_ID;
      }
   
      public int getContractVersion() {
         return TetrapodContract.VERSION;
      } 
       
   }
   
   public static final int ERROR_HOSTNAME_MISMATCH = 12239905; 
   public static final int ERROR_INVALID_ACCOUNT = 14623816; 
   public static final int ERROR_INVALID_CREDENTIALS = 8845805; 
   public static final int ERROR_INVALID_UUID = 398174; 
   public static final int ERROR_ITEM_OWNED = 10331576; 
   public static final int ERROR_NOT_LEADER = 13409358; 
}
