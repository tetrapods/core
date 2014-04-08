var TP = new Tetrapod();

function Tetrapod() {
   var self = this;

   // private vars
   var protocol = {
      request : {},
      response : {},
      struct : {},
      message : {},
      consts : {},
      reverseMap : {}
   };
   var requestCounter = 0;
   var requestHandlers = [];
   var messageHandlers = [];
   var openHandlers = [];
   var closeHandlers = [];
   var socket;

   // public interface
   self.commsLog = false;
   self.register = register;
   self.registerConst = registerConst;
   self.addMessageHandler = addMessageHandler;
   self.send = send;
   self.connect = connect;
   self.consts = protocol.consts;

   function register(type, contractName, structName, contractId, structId) {
      var map = protocol[type];
      var val = {
         contractId : contractId,
         structId : structId
      };
      map[contractName + "." + structName] = val;
      if (map[structName]) {
         map[structName] = 0;
      } else {
         map[structName] = val;
      }
      protocol.reverseMap["" + contractId + "." + structId] = contractName + "." + structName;
   }

   function registerConst(contractName, structName, constName, constValue) {
      var map = protocol["consts"];
      var o = map[contractName + "." + structName] || {};
      map[contractName + "." + structName] = o;
      o[constName] = constValue;
   }

   function consts(name) {
      return protocol["consts"][name];
   }

   function nameOf(arg1, arg2) {
      // call with either a single arg = object with _contractId and _structId
      // or with explicit contractId, structId
      var key;
      if (typeof arg2 !== 'undefined') {
         key = "" + arg1 + "." + arg2;
      } else {
         key = "" + arg1._contractId + "." + arg1._structId;
      }
      return protocol.reverseMap[key];
   }

   function addMessageHandler(message, handler) {
      var val = protocol.message[message];
      if (!val) {
         console.log("unknown message: " + message);
         return;
      }
      if (val === 0) {
         console.log("ambiguous message: " + message);
         return;
      }
      messageHandlers[val.contractId + "." + val.structId] = handler;
   }

   function send(request, args, toId) {
      var val = protocol.request[request];
      toId = typeof toId !== 'undefined' ? toId : 0;

      if (!val) {
         console.log("unknown request: " + request);
         return;
      }
      if (val === 0) {
         console.log("ambiguous request: " + request);
         return;
      }

      var requestId = requestCounter++;
      args._requestId = requestId;
      args._contractId = val.contractId;
      args._structId = val.structId;
      args._toId = toId;
      if (self.commsLog)
         logRequest(args);

      if (socket && socket.readyState == WebSocket.OPEN) {
         socket.send(JSON.stringify(args, null, 3))
      }

      return {
         handle : function(func) {
            requestHandlers[requestId] = func;
         }
      }
   }

   function connect(server, port) {
      port = typeof port !== 'undefined' ? port : 9903;
      if (!window.WebSocket) {
         window.WebSocket = window.MozWebSocket;
      }
      if (!window.WebSocket) {
         return null;
      }
      socket = new WebSocket("ws://" + server + ":" + port + "/sockets");
      socket.onopen = onSocketOpen;
      socket.onmessage = onSocketMessage;
      socket.onclose = onSocketClose;
      socket.onerror = onSocketError;
      return {
         listen : function(onOpen, onClose) {
        	openHandlers = [];
        	closeHandlers = [];
            openHandlers.push(onOpen);
            closeHandlers.push(onClose);
         }
      }
   }

   function logResponse(result) {
      var str = "";
      if (result.isError()) {
         str = "ERROR " + result.errorCode;
      } else if (result._contractId == 1 && result._structId == 2) {
         str = "SUCCESS";
      } else {
         str = JSON.stringify(result);
      }
      console.log("[%d] <- %s%s", result._requestId, nameOf(result), JSON.stringify(result, dropUnderscored));
   }

   function logRequest(result) {
      console.log("[%d] => %s%s", result._requestId, nameOf(result), JSON.stringify(result, dropUnderscored));
   }

   function logMessage(result) {
      console.log("[M:%d] <- %s%s", result._topicId, nameOf(result), JSON.stringify(result, dropUnderscored));
   }

   function dropUnderscored(key, value) {
      if (typeof key == 'string' || key instanceof String) {
         if (key.indexOf("_") == 0)
            return undefined;
      }
      return value;
   }

   // --- socket methods

   function onSocketOpen(event) {
      if (self.commsLog)
         console.log("[socket] open")
      var i, array = openHandlers;
      for (i = 0; i < array.length; i++)
         array[i]();
   }

   function onSocketMessage(event) {
      var result = null;
      if (event.data.indexOf("{") == 0) {
         result = JSON.parse(event.data);
      } else {
         // TODO figure out how to communicate errors
         result = parseInt(event.data);
      }
      if (result._requestId != null) {
         result.isError = function() {
            return result._contractId == 1 && result._structId == 1;
         };
         if (self.commsLog)
            logResponse(result);
         var func = requestHandlers[result._requestId];
         if (func) {
            func(result);
         }
      } else if (result._topicId != null) {
         if (self.commsLog)
            logMessage(result);
         var func = messageHandlers[result._contractId + "." + result._structId];
         if (func) {
            func(result);
         }
      }
   }

   function onSocketClose(event) {
      if (self.commsLog)
         console.log("[socket] closed")
      var i, array = closeHandlers;
      for (i = 0; i < array.length; i++)
         array[i]();
   }

   function onSocketError(event) {
      if (self.commsLog)
         console.log("[socket] error")
   }

}
