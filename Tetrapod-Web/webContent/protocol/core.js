define([], function() { return TP_Core });

function TP_Core(server) {
   var self = this;
   self.name = "Core";

   server.register("response", "Tetrapod", "ERROR", 1, 1);
   server.register("response", "Tetrapod", "SUCCESS", 1, 2);

   self.Core = {};
   self.Core.CONTRACT_ID = 1;
   self.Core.NAME = "Core";
   self.Core.Core = {
      DEFAULT_CLUSTER_PORT : 9902,
      DEFAULT_DIRECT_PORT : 9800,
      DEFAULT_HTTPS_PORT : 9906,
      DEFAULT_HTTP_PORT : 9904,
      DEFAULT_PUBLIC_PORT : 9900,
      DEFAULT_SERVICE_PORT : 9901,
      DIRECT : 1,
      ENVELOPE_BROADCAST : 5,
      ENVELOPE_HANDSHAKE : 1,
      ENVELOPE_MESSAGE : 4,
      ENVELOPE_PING : 6,
      ENVELOPE_PONG : 7,
      ENVELOPE_REQUEST : 2,
      ENVELOPE_RESPONSE : 3,
      STATUS_BUSY : 8,
      STATUS_ERRORS : 256,
      STATUS_FAILED : 32,
      STATUS_GONE : 4,
      STATUS_OVERLOADED : 16,
      STATUS_PASSIVE : 128,
      STATUS_PAUSED : 2,
      STATUS_STARTING : 1,
      STATUS_STOPPING : 64,
      STATUS_WARNINGS : 512,
      TYPE_ADMIN : 3,
      TYPE_ANONYMOUS : 5,
      TYPE_CLIENT : 4,
      TYPE_SERVICE : 2,
      TYPE_TETRAPOD : 1,
      TYPE_WEBAPI : 6,
      UNADDRESSED : 0
   };
   self.Core.MessageHeader = {
      FLAGS_ALTERNATE : 1
   };
   self.Core.Admin = {
      MAX_LOGIN_ATTEMPTS : 5,
      RIGHTS_APP_SET_1 : 256,
      RIGHTS_APP_SET_2 : 512,
      RIGHTS_APP_SET_3 : 1024,
      RIGHTS_APP_SET_4 : 2048,
      RIGHTS_CLUSTER_READ : 1,
      RIGHTS_CLUSTER_WRITE : 2,
      RIGHTS_RESERVED_1 : 16,
      RIGHTS_RESERVED_2 : 32,
      RIGHTS_RESERVED_3 : 64,
      RIGHTS_RESERVED_4 : 128,
      RIGHTS_USER_READ : 4,
      RIGHTS_USER_WRITE : 8
   };
   self.Core.TypeDescriptor = {
      T_BOOLEAN : 1,
      T_BOOLEAN_LIST : 8,
      T_BYTE : 2,
      T_BYTE_LIST : 9,
      T_DOUBLE : 5,
      T_DOUBLE_LIST : 12,
      T_INT : 3,
      T_INT_LIST : 10,
      T_LONG : 4,
      T_LONG_LIST : 11,
      T_STRING : 6,
      T_STRING_LIST : 13,
      T_STRUCT : 7,
      T_STRUCT_LIST : 14
   };
   self.Core.ServiceLogEntry = {
      LEVEL_ALL : 0,
      LEVEL_DEBUG : 20,
      LEVEL_ERROR : 50,
      LEVEL_INFO : 30,
      LEVEL_OFF : 100,
      LEVEL_TRACE : 10,
      LEVEL_WARN : 40
   };
   self.Core.LogHeaderType = {
      REQUEST : 1,
      RESPONSE : 2,
      MESSAGE : 3,
      EVENT : 4
   };
   self.Core.RequestStatsSort = {
      COUNT : 1,
      TOTAL_TIME : 2,
      AVERAGE_TIME : 3,
      ERRORS : 4
   };
   self.Core.SessionType = {
      UNKNOWN : 0,
      WIRE : 1,
      WEB : 2,
      NONE : 3
   };
   
   self.Core.error = {};
   self.Core.error.CONNECTION_CLOSED = 7;
   self.Core.error.FLOOD = 12;
   self.Core.error.INVALID_DATA = 15;
   self.Core.error.INVALID_ENTITY = 9;
   self.Core.error.INVALID_RIGHTS = 8;
   self.Core.error.INVALID_TOKEN = 13;
   self.Core.error.NOT_CONFIGURED = 2718243;
   self.Core.error.PROTOCOL_MISMATCH = 5;
   self.Core.error.RIGHTS_EXPIRED = 10;
   self.Core.error.SECURITY = 16;
   self.Core.error.SERIALIZATION = 4;
   self.Core.error.SERVICE_OVERLOADED = 11;
   self.Core.error.SERVICE_UNAVAILABLE = 2;
   self.Core.error.TIMEOUT = 3;
   self.Core.error.UNKNOWN = 1;
   self.Core.error.UNKNOWN_REQUEST = 6;
   self.Core.error.UNSUPPORTED = 14;
   
   server.register("struct", "Core", "Core", 1, 9088168, null, null);
   server.register("struct", "Core", "RequestHeader", 1, 7165109, null, null);
   server.register("struct", "Core", "ResponseHeader", 1, 675609, null, null);
   server.register("struct", "Core", "MessageHeader", 1, 11760427, null, null);
   server.register("struct", "Core", "CommsLogFileHeader", 1, 11154645, null, null);
   server.register("struct", "Core", "CommsLogHeader", 1, 8830315, null, null);
   server.register("struct", "Core", "MissingStructDef", 1, 14245815, null, null);
   server.register("struct", "Core", "ServiceCommand", 1, 5461687, null, null);
   server.register("struct", "Core", "ServerAddress", 1, 14893956, null, null);
   server.register("struct", "Core", "Admin", 1, 16753598, null, null);
   server.register("request", "Core", "Pause", 1, 14690004, null, null);
   server.register("request", "Core", "Unpause", 1, 10620319, null, null);
   server.register("request", "Core", "Rebalance", 1, 1734199, null, null);
   server.register("request", "Core", "ReleaseExcess", 1, 12519092, null, null);
   server.register("request", "Core", "Purge", 1, 2667367, null, null);
   server.register("request", "Core", "InternalShutdown", 1, 11549681, null, null);
   server.register("request", "Core", "Shutdown", 1, 8989182, null, null);
   server.register("request", "Core", "Restart", 1, 4802943, null, null);
   server.register("request", "Core", "ServiceStatsSubscribe", 1, 13519504, null, null);
   server.register("request", "Core", "ServiceStatsUnsubscribe", 1, 576067, null, null);
   server.register("request", "Core", "ServiceDetails", 1, 14458441, null, null);
   server.register("response", "Core", "ServiceDetails", 1, 12435407, null, null);
   server.register("request", "Core", "ServiceLogs", 1, 13816458, null, null);
   server.register("response", "Core", "ServiceLogs", 1, 6345878, null, null);
   server.register("struct", "Core", "StatPair", 1, 10082177, null, null);
   server.register("struct", "Core", "RequestStat", 1, 12902770, null, null);
   server.register("request", "Core", "ServiceRequestStats", 1, 16134423, null, null);
   server.register("response", "Core", "ServiceRequestStats", 1, 6312573, null, null);
   server.register("request", "Core", "HostInfo", 1, 16426670, null, null);
   server.register("response", "Core", "HostInfo", 1, 7161106, null, null);
   server.register("request", "Core", "HostStats", 1, 4481593, null, null);
   server.register("response", "Core", "HostStats", 1, 15046655, null, null);
   server.register("request", "Core", "ServiceErrorLogs", 1, 16327568, null, null);
   server.register("response", "Core", "ServiceErrorLogs", 1, 9302372, null, null);
   server.register("request", "Core", "ResetServiceErrorLogs", 1, 9359779, null, null);
   server.register("request", "Core", "SetCommsLogLevel", 1, 10256079, null, null);
   server.register("request", "Core", "Debug", 1, 9548115, null, null);
   server.register("request", "Core", "WebAPI", 1, 9321342, null, null);
   server.register("response", "Core", "WebAPI", 1, 9652194, null, null);
   server.register("request", "Core", "DirectConnection", 1, 1361471, null, null);
   server.register("request", "Core", "GetHost", 1, 6376640, null, null);
   server.register("response", "Core", "GetHost", 1, 12598420, null, null);
   server.register("response", "Core", "DirectConnection", 1, 16162197, null, null);
   server.register("request", "Core", "ValidateConnection", 1, 6315662, null, null);
   server.register("response", "Core", "ValidateConnection", 1, 1291890, null, null);
   server.register("request", "Core", "Dummy", 1, 6747086, null, null);
   server.register("message", "Core", "ServiceStats", 1, 469976, null, null);
   server.register("struct", "Core", "WebRoute", 1, 4890284, null, null);
   server.register("struct", "Core", "TypeDescriptor", 1, 6493266, null, null);
   server.register("struct", "Core", "ContractDescription", 1, 7323457, null, null);
   server.register("struct", "Core", "StructDescription", 1, 9642196, null, null);
   server.register("struct", "Core", "ServiceLogEntry", 1, 11222968, null, null);

   return self;
}
