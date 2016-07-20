define([], function() { return TP_Web });

function TP_Web(server) {
   var self = this;
   self.name = "Web";

   server.register("response", "Tetrapod", "ERROR", 1, 1);
   server.register("response", "Tetrapod", "SUCCESS", 1, 2);

   self.Web = {};
   
   self.Web.error = {};
   self.Web.error.UNKNOWN_ALT_ID = 5866283;
   self.Web.error.UNKNOWN_CLIENT_ID = 5653403;
   
   server.register("request", "Web", "KeepAlive", 22, 5512920);
   server.register("request", "Web", "Register", 22, 10895179);
   server.register("response", "Web", "Register", 22, 13376201);
   server.register("request", "Web", "SetAlternateId", 22, 10499521);
   server.register("request", "Web", "GetClientInfo", 22, 3498983);
   server.register("response", "Web", "GetClientInfo", 22, 9293453);
   server.register("request", "Web", "CloseClientConnection", 22, 3310279);
   server.register("request", "Web", "ClientSessions", 22, 1046006);
   server.register("response", "Web", "ClientSessions", 22, 2637706);

   return self;
}
