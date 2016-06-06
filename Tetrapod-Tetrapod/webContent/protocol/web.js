define([], function() { return TP_Web });

function TP_Web(server) {
   var self = this;
   self.name = "Web";

   server.register("response", "Tetrapod", "ERROR", 1, 1);
   server.register("response", "Tetrapod", "SUCCESS", 1, 2);

   self.Web = {};
   
   self.Web.error = {};
   self.Web.error.UNKNOWN_CLIENT_ID = 5653403;
   
   server.register("request", "Web", "KeepAlive", 20, 5512920);
   server.register("request", "Web", "Register", 20, 10895179);
   server.register("response", "Web", "Register", 20, 13376201);
   server.register("request", "Web", "SetAlternateId", 20, 10499521);
   server.register("request", "Web", "GetClientInfo", 20, 3498983);
   server.register("response", "Web", "GetClientInfo", 20, 9293453);

   return self;
}
