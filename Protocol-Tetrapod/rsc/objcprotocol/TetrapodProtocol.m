#import "TPProtocol.h"
#import "TetrapodProtocol.h"

int const Tetrapod_MAX_LOGIN_ATTEMPTS = 5;
int const Tetrapod_RIGHTS_CLUSTER_READ = 1;
int const Tetrapod_RIGHTS_CLUSTER_WRITE = 2;
int const Tetrapod_RIGHTS_USER_READ = 4;
int const Tetrapod_RIGHTS_USER_WRITE = 8;
int const Tetrapod_BUILD = 1;
int const Tetrapod_DEPLOY = 2;
int const Tetrapod_LAUNCH = 3;
int const Tetrapod_FULL_CYCLE = 4;
int const Tetrapod_LAUNCH_PAUSED = 5;
int const Tetrapod_DEPLOY_LATEST = -1;
int const Tetrapod_LAUNCH_DEPLOYED = -1;

int const Tetrapod_ERROR_HOSTNAME_MISMATCH = 12239905;
int const Tetrapod_ERROR_INVALID_ACCOUNT = 14623816;
int const Tetrapod_ERROR_INVALID_CREDENTIALS = 8845805;
int const Tetrapod_ERROR_NOT_PARENT = 2219555;
int const Tetrapod_ERROR_NOT_READY = 12438466;

@implementation TetrapodProtocol : NSObject

+ (void)registerStructs:(TPProtocol *)protocol {
    [protocol addType:@"struct" contract:@"Tetrapod" structName:@"Entity" contractId:1 structId:10171140];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"Register" contractId:1 structId:10895179];
    [protocol addType:@"response" contract:@"Tetrapod" structName:@"Register" contractId:1 structId:13376201];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"ClusterJoin" contractId:1 structId:8294880];
    [protocol addType:@"response" contract:@"Tetrapod" structName:@"ClusterJoin" contractId:1 structId:8947508];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"Unregister" contractId:1 structId:3896262];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"Publish" contractId:1 structId:3171651];
    [protocol addType:@"response" contract:@"Tetrapod" structName:@"Publish" contractId:1 structId:2698673];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"RegistrySubscribe" contractId:1 structId:2572089];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"RegistryUnsubscribe" contractId:1 structId:6168014];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"ServicesSubscribe" contractId:1 structId:7048310];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"ServicesUnsubscribe" contractId:1 structId:11825621];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"ServiceStatusUpdate" contractId:1 structId:4487218];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"AddServiceInformation" contractId:1 structId:14381454];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"LogRegistryStats" contractId:1 structId:10504592];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"AdminLogin" contractId:1 structId:14191480];
    [protocol addType:@"response" contract:@"Tetrapod" structName:@"AdminLogin" contractId:1 structId:4213436];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"AdminAuthorize" contractId:1 structId:12706146];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"AdminCreate" contractId:1 structId:14596683];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"AdminDelete" contractId:1 structId:7421322];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"AdminChangePassword" contractId:1 structId:2877212];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"AdminChangeRights" contractId:1 structId:16102706];
    [protocol addType:@"struct" contract:@"Tetrapod" structName:@"Admin" contractId:1 structId:16753598];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"KeepAlive" contractId:1 structId:5512920];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"AddWebFile" contractId:1 structId:5158759];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"SendWebRoot" contractId:1 structId:16081718];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"SetAlternateId" contractId:1 structId:10499521];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"GetSubscriberCount" contractId:1 structId:9966915];
    [protocol addType:@"response" contract:@"Tetrapod" structName:@"GetSubscriberCount" contractId:1 structId:6503857];
    [protocol addType:@"message" contract:@"Tetrapod" structName:@"Entity" contractId:1 structId:10913291];
    [protocol addType:@"message" contract:@"Tetrapod" structName:@"ClusterMember" contractId:1 structId:1076508];
    [protocol addType:@"message" contract:@"Tetrapod" structName:@"EntityRegistered" contractId:1 structId:1454035];
    [protocol addType:@"message" contract:@"Tetrapod" structName:@"EntityUnregistered" contractId:1 structId:14101566];
    [protocol addType:@"message" contract:@"Tetrapod" structName:@"EntityUpdated" contractId:1 structId:3775838];
    [protocol addType:@"message" contract:@"Tetrapod" structName:@"TopicPublished" contractId:1 structId:6873263];
    [protocol addType:@"message" contract:@"Tetrapod" structName:@"TopicUnpublished" contractId:1 structId:6594504];
    [protocol addType:@"message" contract:@"Tetrapod" structName:@"TopicSubscribed" contractId:1 structId:1498241];
    [protocol addType:@"message" contract:@"Tetrapod" structName:@"TopicUnsubscribed" contractId:1 structId:6934832];
    [protocol addType:@"message" contract:@"Tetrapod" structName:@"EntityListComplete" contractId:1 structId:15616758];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"GetServiceBuildInfo" contractId:1 structId:4482593];
    [protocol addType:@"response" contract:@"Tetrapod" structName:@"GetServiceBuildInfo" contractId:1 structId:4037623];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"ExecuteBuildCommand" contractId:1 structId:7902304];
    [protocol addType:@"message" contract:@"Tetrapod" structName:@"BuildCommandProgress" contractId:1 structId:1646916];
    [protocol addType:@"struct" contract:@"Tetrapod" structName:@"BuildInfo" contractId:1 structId:14488001];
    [protocol addType:@"struct" contract:@"Tetrapod" structName:@"BuildCommand" contractId:1 structId:4239258];
    [protocol addType:@"message" contract:@"Tetrapod" structName:@"ServiceAdded" contractId:1 structId:15116807];
    [protocol addType:@"message" contract:@"Tetrapod" structName:@"ServiceRemoved" contractId:1 structId:1629937];
    [protocol addType:@"message" contract:@"Tetrapod" structName:@"ServiceUpdated" contractId:1 structId:1658756];
    [protocol addType:@"request" contract:@"Tetrapod" structName:@"VerifyEntityToken" contractId:1 structId:8934039];
}

@end
