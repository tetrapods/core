#import "TPProtocol.h"
#import "CoreProtocol.h"

int const Core_UNADDRESSED = 0;
int const Core_DIRECT = 1;
int const Core_TYPE_TETRAPOD = 1;
int const Core_TYPE_SERVICE = 2;
int const Core_TYPE_ADMIN = 3;
int const Core_TYPE_CLIENT = 4;
int const Core_TYPE_ANONYMOUS = 5;
int const Core_TYPE_WEBAPI = 6;
int const Core_DEFAULT_PUBLIC_PORT = 9900;
int const Core_DEFAULT_SERVICE_PORT = 9901;
int const Core_DEFAULT_CLUSTER_PORT = 9902;
int const Core_DEFAULT_HTTP_PORT = 9904;
int const Core_DEFAULT_HTTPS_PORT = 9906;
int const Core_DEFAULT_DIRECT_PORT = 9800;
int const Core_STATUS_STARTING = 1;
int const Core_STATUS_PAUSED = 2;
int const Core_STATUS_GONE = 4;
int const Core_STATUS_BUSY = 8;
int const Core_STATUS_OVERLOADED = 16;
int const Core_STATUS_FAILED = 32;
int const Core_STATUS_STOPPING = 64;
int const Core_STATUS_PASSIVE = 128;
int const Core_STATUS_ERRORS = 256;
int const Core_STATUS_WARNINGS = 512;
int const Core_ENVELOPE_HANDSHAKE = 1;
int const Core_ENVELOPE_REQUEST = 2;
int const Core_ENVELOPE_RESPONSE = 3;
int const Core_ENVELOPE_MESSAGE = 4;
int const Core_ENVELOPE_BROADCAST = 5;
int const Core_ENVELOPE_PING = 6;
int const Core_ENVELOPE_PONG = 7;
int const Core_TO_TOPIC = 1;
int const Core_TO_ENTITY = 2;
int const Core_TO_ALTERNATE = 3;
int const Core_T_BOOLEAN = 1;
int const Core_T_BYTE = 2;
int const Core_T_INT = 3;
int const Core_T_LONG = 4;
int const Core_T_DOUBLE = 5;
int const Core_T_STRING = 6;
int const Core_T_STRUCT = 7;
int const Core_T_BOOLEAN_LIST = 8;
int const Core_T_BYTE_LIST = 9;
int const Core_T_INT_LIST = 10;
int const Core_T_LONG_LIST = 11;
int const Core_T_DOUBLE_LIST = 12;
int const Core_T_STRING_LIST = 13;
int const Core_T_STRUCT_LIST = 14;
int const Core_LEVEL_ALL = 0;
int const Core_LEVEL_TRACE = 10;
int const Core_LEVEL_DEBUG = 20;
int const Core_LEVEL_INFO = 30;
int const Core_LEVEL_WARN = 40;
int const Core_LEVEL_ERROR = 50;
int const Core_LEVEL_OFF = 100;

int const Core_ERROR_CONNECTION_CLOSED = 7;
int const Core_ERROR_FLOOD = 12;
int const Core_ERROR_INVALID_ENTITY = 9;
int const Core_ERROR_INVALID_RIGHTS = 8;
int const Core_ERROR_INVALID_TOKEN = 13;
int const Core_ERROR_NOT_CONFIGURED = 2718243;
int const Core_ERROR_PROTOCOL_MISMATCH = 5;
int const Core_ERROR_RIGHTS_EXPIRED = 10;
int const Core_ERROR_SERIALIZATION = 4;
int const Core_ERROR_SERVICE_OVERLOADED = 11;
int const Core_ERROR_SERVICE_UNAVAILABLE = 2;
int const Core_ERROR_TIMEOUT = 3;
int const Core_ERROR_UNKNOWN = 1;
int const Core_ERROR_UNKNOWN_REQUEST = 6;
int const Core_ERROR_UNSUPPORTED = 14;

@implementation CoreProtocol : NSObject

+ (void)registerStructs:(TPProtocol *)protocol {
    [protocol addType:@"struct" contract:@"Core" structName:@"Core" contractId:1 structId:9088168];
    [protocol addType:@"struct" contract:@"Core" structName:@"RequestHeader" contractId:1 structId:7165109];
    [protocol addType:@"struct" contract:@"Core" structName:@"ResponseHeader" contractId:1 structId:675609];
    [protocol addType:@"struct" contract:@"Core" structName:@"MessageHeader" contractId:1 structId:11760427];
    [protocol addType:@"struct" contract:@"Core" structName:@"ServiceCommand" contractId:1 structId:5461687];
    [protocol addType:@"struct" contract:@"Core" structName:@"ServerAddress" contractId:1 structId:14893956];
    [protocol addType:@"request" contract:@"Core" structName:@"Pause" contractId:1 structId:14690004];
    [protocol addType:@"request" contract:@"Core" structName:@"Unpause" contractId:1 structId:10620319];
    [protocol addType:@"request" contract:@"Core" structName:@"Shutdown" contractId:1 structId:8989182];
    [protocol addType:@"request" contract:@"Core" structName:@"Restart" contractId:1 structId:4802943];
    [protocol addType:@"request" contract:@"Core" structName:@"ServiceStatsSubscribe" contractId:1 structId:13519504];
    [protocol addType:@"request" contract:@"Core" structName:@"ServiceStatsUnsubscribe" contractId:1 structId:576067];
    [protocol addType:@"request" contract:@"Core" structName:@"ServiceDetails" contractId:1 structId:14458441];
    [protocol addType:@"response" contract:@"Core" structName:@"ServiceDetails" contractId:1 structId:12435407];
    [protocol addType:@"request" contract:@"Core" structName:@"ServiceLogs" contractId:1 structId:13816458];
    [protocol addType:@"response" contract:@"Core" structName:@"ServiceLogs" contractId:1 structId:6345878];
    [protocol addType:@"request" contract:@"Core" structName:@"ServiceErrorLogs" contractId:1 structId:16327568];
    [protocol addType:@"response" contract:@"Core" structName:@"ServiceErrorLogs" contractId:1 structId:9302372];
    [protocol addType:@"request" contract:@"Core" structName:@"ResetServiceErrorLogs" contractId:1 structId:9359779];
    [protocol addType:@"request" contract:@"Core" structName:@"SetCommsLogLevel" contractId:1 structId:10256079];
    [protocol addType:@"request" contract:@"Core" structName:@"WebAPI" contractId:1 structId:9321342];
    [protocol addType:@"response" contract:@"Core" structName:@"WebAPI" contractId:1 structId:9652194];
    [protocol addType:@"request" contract:@"Core" structName:@"DirectConnection" contractId:1 structId:1361471];
    [protocol addType:@"response" contract:@"Core" structName:@"DirectConnection" contractId:1 structId:16162197];
    [protocol addType:@"request" contract:@"Core" structName:@"ValidateConnection" contractId:1 structId:6315662];
    [protocol addType:@"response" contract:@"Core" structName:@"ValidateConnection" contractId:1 structId:1291890];
    [protocol addType:@"request" contract:@"Core" structName:@"Dummy" contractId:1 structId:6747086];
    [protocol addType:@"message" contract:@"Core" structName:@"ServiceStats" contractId:1 structId:469976];
    [protocol addType:@"struct" contract:@"Core" structName:@"Subscriber" contractId:1 structId:16013581];
    [protocol addType:@"struct" contract:@"Core" structName:@"WebRoute" contractId:1 structId:4890284];
    [protocol addType:@"struct" contract:@"Core" structName:@"TypeDescriptor" contractId:1 structId:6493266];
    [protocol addType:@"struct" contract:@"Core" structName:@"StructDescription" contractId:1 structId:9642196];
    [protocol addType:@"struct" contract:@"Core" structName:@"ServiceLogEntry" contractId:1 structId:11222968];
}

@end
