define([], function() { return TP_Tetrapod });

function TP_Tetrapod(server) {
   var self = this;
   self.name = "Tetrapod";

   server.register("response", "Tetrapod", "ERROR", 1, 1);
   server.register("response", "Tetrapod", "SUCCESS", 1, 2);

   self.Tetrapod = {};
   self.Tetrapod.CONTRACT_ID = 1;
   self.Tetrapod.NAME = "Tetrapod";
   self.Tetrapod.BuildCommand = {
      BUILD : 1,
      DEPLOY : 2,
      DEPLOY_LATEST : -1,
      FULL_CYCLE : 4,
      LAUNCH : 3,
      LAUNCH_DEPLOYED : -1,
      LAUNCH_PAUSED : 5
   };
   self.Tetrapod.MAX_PARENTS = 0x000007FF;
   self.Tetrapod.MAX_ID = 0x000FFFFF;
   self.Tetrapod.PARENT_ID_SHIFT = 20;
   self.Tetrapod.PARENT_ID_MASK = 0x7FF00000;
   
   self.Tetrapod.error = {};
   self.Tetrapod.error.HOSTNAME_MISMATCH = 12239905;
   self.Tetrapod.error.INVALID_ACCOUNT = 14623816;
   self.Tetrapod.error.INVALID_CREDENTIALS = 8845805;
   self.Tetrapod.error.INVALID_UUID = 398174;
   self.Tetrapod.error.ITEM_OWNED = 10331576;
   self.Tetrapod.error.NOT_LEADER = 13409358;
   
   server.register("struct", "Tetrapod", "Entity", 1, 10171140, null, null);
   server.register("request", "Tetrapod", "Register", 1, 10895179, null, null);
   server.register("response", "Tetrapod", "Register", 1, 13376201, null, null);
   server.register("request", "Tetrapod", "ClusterJoin", 1, 8294880, null, null);
   server.register("request", "Tetrapod", "Unregister", 1, 3896262, null, null);
   server.register("request", "Tetrapod", "ServicesSubscribe", 1, 7048310, null, null);
   server.register("request", "Tetrapod", "ServicesUnsubscribe", 1, 11825621, null, null);
   server.register("request", "Tetrapod", "ServiceStatusUpdate", 1, 4487218, null, null);
   server.register("request", "Tetrapod", "AddServiceInformation", 1, 14381454, null, null);
   server.register("request", "Tetrapod", "LogRegistryStats", 1, 10504592, null, null);
   server.register("request", "Tetrapod", "AdminLogin", 1, 14191480, null, null);
   server.register("response", "Tetrapod", "AdminLogin", 1, 4213436, null, null);
   server.register("request", "Tetrapod", "AdminAuthorize", 1, 12706146, null, null);
   server.register("response", "Tetrapod", "AdminAuthorize", 1, 8072638, null, null);
   server.register("request", "Tetrapod", "AdminSessionToken", 1, 15044284, null, null);
   server.register("response", "Tetrapod", "AdminSessionToken", 1, 1057000, null, null);
   server.register("request", "Tetrapod", "AdminCreate", 1, 14596683, null, null);
   server.register("request", "Tetrapod", "AdminDelete", 1, 7421322, null, null);
   server.register("request", "Tetrapod", "AdminChangePassword", 1, 2877212, null, null);
   server.register("request", "Tetrapod", "AdminResetPassword", 1, 868729, null, null);
   server.register("request", "Tetrapod", "AdminChangeRights", 1, 16102706, null, null);
   server.register("message", "Tetrapod", "Entity", 1, 10913291, null, null);
   server.register("message", "Tetrapod", "TopicPublished", 1, 6873263, null, null);
   server.register("message", "Tetrapod", "TopicUnpublished", 1, 6594504, null, null);
   server.register("message", "Tetrapod", "TopicSubscribed", 1, 1498241, null, null);
   server.register("message", "Tetrapod", "TopicUnsubscribed", 1, 6934832, null, null);
   server.register("message", "Tetrapod", "TopicNotFound", 1, 2478456, null, null);
   server.register("message", "Tetrapod", "SubscriberNotFound", 1, 995961, null, null);
   server.register("request", "Tetrapod", "GetServiceBuildInfo", 1, 4482593, null, null);
   server.register("response", "Tetrapod", "GetServiceBuildInfo", 1, 4037623, null, null);
   server.register("request", "Tetrapod", "ExecuteBuildCommand", 1, 7902304, null, null);
   server.register("message", "Tetrapod", "BuildCommandProgress", 1, 1646916, null, null);
   server.register("struct", "Tetrapod", "BuildInfo", 1, 14488001, null, null);
   server.register("struct", "Tetrapod", "BuildCommand", 1, 4239258, null, null);
   server.register("message", "Tetrapod", "ServiceAdded", 1, 15116807, null, null);
   server.register("message", "Tetrapod", "ServiceRemoved", 1, 1629937, null, null);
   server.register("message", "Tetrapod", "ServiceUpdated", 1, 1658756, null, null);
   server.register("request", "Tetrapod", "RaftLeader", 1, 13647638, null, null);
   server.register("response", "Tetrapod", "RaftLeader", 1, 10320426, null, null);
   server.register("request", "Tetrapod", "RaftStats", 1, 15652108, null, null);
   server.register("response", "Tetrapod", "RaftStats", 1, 13186680, null, null);
   server.register("request", "Tetrapod", "AdminSubscribe", 1, 4415191, null, null);
   server.register("response", "Tetrapod", "AdminSubscribe", 1, 5933629, null, null);
   server.register("struct", "Tetrapod", "ClusterProperty", 1, 16245306, null, null);
   server.register("message", "Tetrapod", "ClusterPropertyAdded", 1, 5735715, null, null);
   server.register("message", "Tetrapod", "ClusterPropertyRemoved", 1, 12285117, null, null);
   server.register("message", "Tetrapod", "ClusterSynced", 1, 12460484, null, null);
   server.register("request", "Tetrapod", "InternalSetClusterProperty", 1, 15539010, null, null);
   server.register("request", "Tetrapod", "SetClusterProperty", 1, 11003897, null, null);
   server.register("request", "Tetrapod", "DelClusterProperty", 1, 15970020, null, null);
   server.register("message", "Tetrapod", "RegisterContract", 1, 11935907, null, null);
   server.register("message", "Tetrapod", "ClusterMember", 1, 1076508, null, null);
   server.register("struct", "Tetrapod", "WebRootDef", 1, 943242, null, null);
   server.register("message", "Tetrapod", "WebRootAdded", 1, 270402, null, null);
   server.register("message", "Tetrapod", "WebRootRemoved", 1, 13146496, null, null);
   server.register("request", "Tetrapod", "SetWebRoot", 1, 4029010, null, null);
   server.register("request", "Tetrapod", "DelWebRoot", 1, 11212431, null, null);
   server.register("message", "Tetrapod", "AdminUserAdded", 1, 2316676, null, null);
   server.register("message", "Tetrapod", "AdminUserRemoved", 1, 9416406, null, null);
   server.register("request", "Tetrapod", "Lock", 1, 3921081, null, null);
   server.register("response", "Tetrapod", "Lock", 1, 7264127, null, null);
   server.register("request", "Tetrapod", "Unlock", 1, 426316, null, null);
   server.register("request", "Tetrapod", "Snapshot", 1, 7083092, null, null);
   server.register("struct", "Tetrapod", "Owner", 1, 2276990, null, null);
   server.register("request", "Tetrapod", "ClaimOwnership", 1, 4158859, null, null);
   server.register("response", "Tetrapod", "ClaimOwnership", 1, 16599817, null, null);
   server.register("request", "Tetrapod", "RetainOwnership", 1, 3539584, null, null);
   server.register("request", "Tetrapod", "ReleaseOwnership", 1, 3927214, null, null);
   server.register("request", "Tetrapod", "SubscribeOwnership", 1, 15739199, null, null);
   server.register("request", "Tetrapod", "UnsubscribeOwnership", 1, 5167974, null, null);
   server.register("message", "Tetrapod", "ClaimOwnership", 1, 500513, null, null);
   server.register("message", "Tetrapod", "RetainOwnership", 1, 12503106, null, null);
   server.register("message", "Tetrapod", "ReleaseOwnership", 1, 9542348, null, null);
   server.register("request", "Tetrapod", "NagiosStatus", 1, 12047571, null, null);
   server.register("response", "Tetrapod", "NagiosStatus", 1, 15307585, null, null);
   server.register("message", "Tetrapod", "NagiosStatus", 1, 6683577, null, null);

   return self;
}
