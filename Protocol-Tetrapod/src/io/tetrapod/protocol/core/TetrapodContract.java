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

@SuppressWarnings("unused")
public class TetrapodContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "Tetrapod";
   public static final int CONTRACT_ID = 1;
   
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
      , GetEntityInfoRequest.Handler
      , GetServiceBuildInfoRequest.Handler
      , GetSubscriberCountRequest.Handler
      , KeepAliveRequest.Handler
      , LockRequest.Handler
      , LogRegistryStatsRequest.Handler
      , RaftStatsRequest.Handler
      , RegisterRequest.Handler
      , RegistrySubscribeRequest.Handler
      , RegistryUnsubscribeRequest.Handler
      , ReleaseOwnershipRequest.Handler
      , RetainOwnershipRequest.Handler
      , ServiceStatusUpdateRequest.Handler
      , ServicesSubscribeRequest.Handler
      , ServicesUnsubscribeRequest.Handler
      , SetAlternateIdRequest.Handler
      , SetClusterPropertyRequest.Handler
      , SetEntityReferrerRequest.Handler
      , SetWebRootRequest.Handler
      , SnapshotRequest.Handler
      , SubscribeOwnershipRequest.Handler
      , TetrapodClientSessionsRequest.Handler
      , UnlockRequest.Handler
      , UnregisterRequest.Handler
      , UnsubscribeOwnershipRequest.Handler
      , VerifyEntityTokenRequest.Handler
      {}
   
   public Structure[] getRequests() {
      return new Structure[] {
         new RegisterRequest(),
         new ClusterJoinRequest(),
         new UnregisterRequest(),
         new RegistrySubscribeRequest(),
         new RegistryUnsubscribeRequest(),
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
         new KeepAliveRequest(),
         new SetAlternateIdRequest(),
         new GetSubscriberCountRequest(),
         new SetEntityReferrerRequest(),
         new GetEntityInfoRequest(),
         new GetServiceBuildInfoRequest(),
         new ExecuteBuildCommandRequest(),
         new VerifyEntityTokenRequest(),
         new RaftStatsRequest(),
         new AdminSubscribeRequest(),
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
         new TetrapodClientSessionsRequest(),
      };
   }
   
   public Structure[] getResponses() {
      return new Structure[] {
         new RegisterResponse(),
         new AdminLoginResponse(),
         new AdminAuthorizeResponse(),
         new AdminSessionTokenResponse(),
         new GetSubscriberCountResponse(),
         new GetEntityInfoResponse(),
         new GetServiceBuildInfoResponse(),
         new RaftStatsResponse(),
         new LockResponse(),
         new ClaimOwnershipResponse(),
         new TetrapodClientSessionsResponse(),
      };
   }
   
   public Structure[] getMessages() {
      return new Structure[] {
         new EntityMessage(),
         new EntityRegisteredMessage(),
         new EntityUnregisteredMessage(),
         new EntityUpdatedMessage(),
         new TopicPublishedMessage(),
         new TopicUnpublishedMessage(),
         new TopicSubscribedMessage(),
         new TopicUnsubscribedMessage(),
         new EntityListCompleteMessage(),
         new BuildCommandProgressMessage(),
         new ServiceAddedMessage(),
         new ServiceRemovedMessage(),
         new ServiceUpdatedMessage(),
         new ClusterPropertyAddedMessage(),
         new ClusterPropertyRemovedMessage(),
         new ClusterSyncedMessage(),
         new ClusterMemberMessage(),
         new WebRootAddedMessage(),
         new WebRootRemovedMessage(),
         new AdminUserAddedMessage(),
         new AdminUserRemovedMessage(),
         new ClaimOwnershipMessage(),
         new RetainOwnershipMessage(),
         new ReleaseOwnershipMessage(),
      };
   }
   
   public Structure[] getStructs() {
      return new Structure[] {
         new Entity(),
         new Admin(),
         new BuildInfo(),
         new BuildCommand(),
         new ClusterProperty(),
         new WebRootDef(),
         new Owner(),
      };
   }
   
   public String getName() {
      return TetrapodContract.NAME;
   } 
   
   public int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }
   
   public int getContractVersion() {
      return TetrapodContract.VERSION;
   }
   
   public WebRoute[] getWebRoutes() {
      return new WebRoute[] {
         
      };
   }

   public static class Cluster extends Contract {
      public static interface API extends
         ClusterMemberMessage.Handler,
         ClusterPropertyAddedMessage.Handler,
         ClusterPropertyRemovedMessage.Handler,
         ClusterSyncedMessage.Handler
         {}
         
      public Structure[] getMessages() {
         return new Structure[] {
            new ClusterMemberMessage(),
            new ClusterPropertyAddedMessage(),
            new ClusterPropertyRemovedMessage(),
            new ClusterSyncedMessage(),
         };
      }
      
      public String getName() {
         return TetrapodContract.NAME;
      }
      
      public int getContractId() {
         return TetrapodContract.CONTRACT_ID;
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
       
      public int getContractVersion() {
         return TetrapodContract.VERSION;
      } 
       
   }
      
   public static class Registry extends Contract {
      public static interface API extends
         EntityListCompleteMessage.Handler,
         EntityRegisteredMessage.Handler,
         EntityUnregisteredMessage.Handler,
         EntityUpdatedMessage.Handler,
         TopicPublishedMessage.Handler,
         TopicSubscribedMessage.Handler,
         TopicUnpublishedMessage.Handler,
         TopicUnsubscribedMessage.Handler
         {}
         
      public Structure[] getMessages() {
         return new Structure[] {
            new EntityListCompleteMessage(),
            new EntityRegisteredMessage(),
            new EntityUnregisteredMessage(),
            new EntityUpdatedMessage(),
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
       
      public int getContractVersion() {
         return TetrapodContract.VERSION;
      } 
       
   }
      
   public static final int ERROR_HOSTNAME_MISMATCH = 12239905; 
   public static final int ERROR_INVALID_ACCOUNT = 14623816; 
   public static final int ERROR_INVALID_CREDENTIALS = 8845805; 
   public static final int ERROR_INVALID_UUID = 398174; 
   public static final int ERROR_ITEM_OWNED = 10331576; 
   public static final int ERROR_UNKNOWN_ENTITY_ID = 15576171; 
}
