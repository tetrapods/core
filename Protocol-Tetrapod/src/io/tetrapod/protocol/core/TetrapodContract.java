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
   
   public static interface API extends APIHandler
      , AddServiceInformationRequest.Handler
      , AdminAuthorizeRequest.Handler
      , AdminChangePasswordRequest.Handler
      , AdminChangeRightsRequest.Handler
      , AdminCreateRequest.Handler
      , AdminDeleteRequest.Handler
      , AdminLoginRequest.Handler
      , AdminSubscribeRequest.Handler
      , ClusterJoinRequest.Handler
      , ClusterLeaveRequest.Handler
      , DelClusterPropertyRequest.Handler
      , DelWebRootRequest.Handler
      , ExecuteBuildCommandRequest.Handler
      , GetEntityInfoRequest.Handler
      , GetServiceBuildInfoRequest.Handler
      , GetSubscriberCountRequest.Handler
      , IssuePeerIdRequest.Handler
      , KeepAliveRequest.Handler
      , LogRegistryStatsRequest.Handler
      , PublishRequest.Handler
      , RaftStatsRequest.Handler
      , RegisterRequest.Handler
      , RegistrySubscribeRequest.Handler
      , RegistryUnsubscribeRequest.Handler
      , ServiceStatusUpdateRequest.Handler
      , ServicesSubscribeRequest.Handler
      , ServicesUnsubscribeRequest.Handler
      , SetAlternateIdRequest.Handler
      , SetClusterPropertyRequest.Handler
      , SetWebRootRequest.Handler
      , UnregisterRequest.Handler
      , VerifyEntityTokenRequest.Handler
      {}
   
   public Structure[] getRequests() {
      return new Structure[] {
         new RegisterRequest(),
         new IssuePeerIdRequest(),
         new ClusterJoinRequest(),
         new ClusterLeaveRequest(),
         new UnregisterRequest(),
         new PublishRequest(),
         new RegistrySubscribeRequest(),
         new RegistryUnsubscribeRequest(),
         new ServicesSubscribeRequest(),
         new ServicesUnsubscribeRequest(),
         new ServiceStatusUpdateRequest(),
         new AddServiceInformationRequest(),
         new LogRegistryStatsRequest(),
         new AdminLoginRequest(),
         new AdminAuthorizeRequest(),
         new AdminCreateRequest(),
         new AdminDeleteRequest(),
         new AdminChangePasswordRequest(),
         new AdminChangeRightsRequest(),
         new KeepAliveRequest(),
         new SetAlternateIdRequest(),
         new GetSubscriberCountRequest(),
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
      };
   }
   
   public Structure[] getResponses() {
      return new Structure[] {
         new RegisterResponse(),
         new IssuePeerIdResponse(),
         new PublishResponse(),
         new AdminLoginResponse(),
         new AdminAuthorizeResponse(),
         new GetSubscriberCountResponse(),
         new GetEntityInfoResponse(),
         new GetServiceBuildInfoResponse(),
         new RaftStatsResponse(),
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
   public static final int ERROR_NOT_PARENT = 2219555; 
   public static final int ERROR_NOT_READY = 12438466; 
   public static final int ERROR_UNKNOWN_ENTITY_ID = 15576171; 
}
