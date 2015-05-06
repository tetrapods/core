define([], function() { return TP_Tetrapod });

function TP_Tetrapod(server) {

   server.register("response", "Tetrapod", "ERROR", 1, 1);
   server.register("response", "Tetrapod", "SUCCESS", 1, 2);

   server.registerConst("Tetrapod", "Admin", "MAX_LOGIN_ATTEMPTS", 5);
   server.registerConst("Tetrapod", "Admin", "RIGHTS_CLUSTER_READ", 1);
   server.registerConst("Tetrapod", "Admin", "RIGHTS_CLUSTER_WRITE", 2);
   server.registerConst("Tetrapod", "Admin", "RIGHTS_USER_READ", 4);
   server.registerConst("Tetrapod", "Admin", "RIGHTS_USER_WRITE", 8);
   server.registerConst("Tetrapod", "BuildCommand", "BUILD", 1);
   server.registerConst("Tetrapod", "BuildCommand", "DEPLOY", 2);
   server.registerConst("Tetrapod", "BuildCommand", "LAUNCH", 3);
   server.registerConst("Tetrapod", "BuildCommand", "FULL_CYCLE", 4);
   server.registerConst("Tetrapod", "BuildCommand", "LAUNCH_PAUSED", 5);
   server.registerConst("Tetrapod", "BuildCommand", "DEPLOY_LATEST", -1);
   server.registerConst("Tetrapod", "BuildCommand", "LAUNCH_DEPLOYED", -1);
   
   server.registerConst("Tetrapod", "null", "HOSTNAME_MISMATCH", 12239905);
   server.registerConst("Tetrapod", "null", "INVALID_ACCOUNT", 14623816);
   server.registerConst("Tetrapod", "null", "INVALID_CREDENTIALS", 8845805);
   server.registerConst("Tetrapod", "null", "INVALID_UUID", 398174);
   server.registerConst("Tetrapod", "null", "NOT_PARENT", 2219555);
   server.registerConst("Tetrapod", "null", "NOT_READY", 12438466);
   server.registerConst("Tetrapod", "null", "UNKNOWN_ENTITY_ID", 15576171);
   
   server.register("struct", "Tetrapod", "Entity", 1, 10171140);
   server.register("request", "Tetrapod", "Register", 1, 10895179);
   server.register("response", "Tetrapod", "Register", 1, 13376201);
   server.register("request", "Tetrapod", "IssuePeerId", 1, 10809624);
   server.register("response", "Tetrapod", "IssuePeerId", 1, 14036188);
   server.register("request", "Tetrapod", "ClusterJoin", 1, 8294880);
   server.register("request", "Tetrapod", "ClusterLeave", 1, 12863875);
   server.register("request", "Tetrapod", "Unregister", 1, 3896262);
   server.register("request", "Tetrapod", "Publish", 1, 3171651);
   server.register("response", "Tetrapod", "Publish", 1, 2698673);
   server.register("request", "Tetrapod", "RegistrySubscribe", 1, 2572089);
   server.register("request", "Tetrapod", "RegistryUnsubscribe", 1, 6168014);
   server.register("request", "Tetrapod", "ServicesSubscribe", 1, 7048310);
   server.register("request", "Tetrapod", "ServicesUnsubscribe", 1, 11825621);
   server.register("request", "Tetrapod", "ServiceStatusUpdate", 1, 4487218);
   server.register("request", "Tetrapod", "AddServiceInformation", 1, 14381454);
   server.register("request", "Tetrapod", "LogRegistryStats", 1, 10504592);
   server.register("request", "Tetrapod", "AdminLogin", 1, 14191480);
   server.register("response", "Tetrapod", "AdminLogin", 1, 4213436);
   server.register("request", "Tetrapod", "AdminAuthorize", 1, 12706146);
   server.register("request", "Tetrapod", "AdminCreate", 1, 14596683);
   server.register("request", "Tetrapod", "AdminDelete", 1, 7421322);
   server.register("request", "Tetrapod", "AdminChangePassword", 1, 2877212);
   server.register("request", "Tetrapod", "AdminChangeRights", 1, 16102706);
   server.register("struct", "Tetrapod", "Admin", 1, 16753598);
   server.register("request", "Tetrapod", "KeepAlive", 1, 5512920);
   server.register("request", "Tetrapod", "AddWebFile", 1, 5158759);
   server.register("request", "Tetrapod", "SendWebRoot", 1, 16081718);
   server.register("request", "Tetrapod", "SetAlternateId", 1, 10499521);
   server.register("request", "Tetrapod", "GetSubscriberCount", 1, 9966915);
   server.register("response", "Tetrapod", "GetSubscriberCount", 1, 6503857);
   server.register("request", "Tetrapod", "GetEntityInfo", 1, 14891231);
   server.register("response", "Tetrapod", "GetEntityInfo", 1, 11007413);
   server.register("message", "Tetrapod", "Entity", 1, 10913291);
   server.register("message", "Tetrapod", "ClusterMember", 1, 1076508);
   server.register("message", "Tetrapod", "EntityRegistered", 1, 1454035);
   server.register("message", "Tetrapod", "EntityUnregistered", 1, 14101566);
   server.register("message", "Tetrapod", "EntityUpdated", 1, 3775838);
   server.register("message", "Tetrapod", "TopicPublished", 1, 6873263);
   server.register("message", "Tetrapod", "TopicUnpublished", 1, 6594504);
   server.register("message", "Tetrapod", "TopicSubscribed", 1, 1498241);
   server.register("message", "Tetrapod", "TopicUnsubscribed", 1, 6934832);
   server.register("message", "Tetrapod", "EntityListComplete", 1, 15616758);
   server.register("request", "Tetrapod", "GetServiceBuildInfo", 1, 4482593);
   server.register("response", "Tetrapod", "GetServiceBuildInfo", 1, 4037623);
   server.register("request", "Tetrapod", "ExecuteBuildCommand", 1, 7902304);
   server.register("message", "Tetrapod", "BuildCommandProgress", 1, 1646916);
   server.register("struct", "Tetrapod", "BuildInfo", 1, 14488001);
   server.register("struct", "Tetrapod", "BuildCommand", 1, 4239258);
   server.register("message", "Tetrapod", "ServiceAdded", 1, 15116807);
   server.register("message", "Tetrapod", "ServiceRemoved", 1, 1629937);
   server.register("message", "Tetrapod", "ServiceUpdated", 1, 1658756);
   server.register("request", "Tetrapod", "VerifyEntityToken", 1, 8934039);
   server.register("request", "Tetrapod", "RaftStats", 1, 15652108);
   server.register("response", "Tetrapod", "RaftStats", 1, 13186680);

}
