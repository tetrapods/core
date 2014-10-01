define([], function() {
   return TP_Server;
});

function TP_Server() {
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
   var requestHandlers = new Object();
   var messageHandlers = [];
   var openHandlers = [];
   var closeHandlers = [];
   var socket;
   var simulator = null;
   var lastHeardFrom = 0;
   var lastSpokeTo = 0;
   var keepAliveRequestId;

   // public interface
   self.forceLongPolling = false;
   self.commsLog = false;
   self.commsLogKeepAlives = false;
   self.register = register;
   self.registerConst = registerConst;
   self.addMessageHandler = addMessageHandler;
   self.send = send; // to any
   self.sendTo = sendTo;
   self.sendDirect = sendDirect;
   self.sendRequest = sendRequest;
   self.connect = connect;
   self.disconnect = disconnect;
   self.nameOf = nameOf;
   self.consts = protocol.consts;
   self.connected = false;
   self.polling = false;
   self.setSimulator = function(s) {
      simulator = s;
   };

   for (var i = 0; i < arguments.length; i++) {
      new arguments[i](self);
   }

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
      var o;
      if (structName != null && structName != "null") {
         o = map[contractName + "." + structName] || {};
         map[contractName + "." + structName] = o;
      } else {
         o = map[contractName] || {};
         map[contractName] = o;
      }
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

   // sends to any available service for this request's contract
   function send(request, args, requestHandler) {
      sendTo(request, args, 0, requestHandler);
   }

   // sends direct to the other end of the connection
   function sendDirect(request, args, requestHandler) {
      sendTo(request, args, 1, requestHandler);
   }

   // sends to the passed in toId
   function sendTo(request, args, toId, requestHandler) {
      var val = protocol.request[request];
      if (!val) {
         console.log("unknown request: " + request);
         return;
      }
      if (val === 0) {
         console.log("ambiguous request: " + request);
         return;
      }
      return sendRequest(val.contractId, val.structId, args, toId, requestHandler);
   }

   // dispatch a request
   function sendRequest(contractId, structId, args, toId, requestHandler) {
      toId = typeof toId !== 'undefined' ? toId : 0;
      var requestId = requestCounter++;
      args._requestId = requestId;
      args._contractId = contractId;
      args._structId = structId;
      args._toId = toId;
      if (isKeepAlive(contractId, structId) && !self.commsLogKeepAlives) {
         keepAliveRequestId = requestId;
      } else {
         if (self.commsLog)
            logRequest(args, toId);
      }

      if (requestHandler) {
         requestHandlers[requestId] = requestHandler;
      }

      if (simulator != null) {
         var resp = simulator.request(request, args, toId);
         var i;
         for (i = 0; i < resp.messages.length; i++) {
            var mess = resp.messages[i];
            handleMessage(mess);
         }
         handleResponse(resp.response);
      }

      if (self.polling) {
         lastSpokeTo = Date.now();
         sendRPC(args);
      } else {
         if (isConnected()) {
            var data = JSON.stringify(args, null, 3);
            if (data.length < 1024 * 128) {
               lastSpokeTo = Date.now();
               socket.send(data);
            } else {
               console.log("RPC too big : " + data.length + "\n" + data);
               handleResponse(makeError(requestId, 1)); // UNKNOWN 
            }
         } else {
            handleResponse(makeError(requestId, 7)); // CONNECTION_CLOSED 
         }
      }
   }

   function makeError(requestId, code) {
      return {
         _requestId : requestId,
         _contractId : 1,
         _structId : 1,
         errorCode : code
      }
   }

   function isConnected() {
      return (socket && socket.readyState == WebSocket.OPEN);
   }

   function connect(server, secure, port) {
      port = typeof port !== 'undefined' ? port : window.location.port;
      if (!window.WebSocket) {
         window.WebSocket = window.MozWebSocket;
      }

      // I suspect Safari 5.1 fails because it claims to have websockets but the implementation is only partial, so we detect the user agent and force it here
      // Mozilla/5.0 (iPad; CPU OS 5_1 like Mac OS X) AppleWebKit/534.46 (KHTML, like Gecko) Version/5.1 Mobile/9B176 Safari/7534.48.3
      if (navigator.userAgent.indexOf("AppleWebKit") > 0 && navigator.userAgent.indexOf("Version/5.1") > 0) {
         self.forceLongPolling = true;
      }

      // fall back to long polling if we have no WebSocket support
      if (!window.WebSocket || self.forceLongPolling) {
         // have to long poll to same-origin
         return startPollingSession(window.location.hostname, secure);
      }

      if (simulator != null) {
         return {
            listen : function(onOpen, onClose) {
               onOpen();
            }
         }
      }

      // support for websocket spec 76 and make sure we're closed before unloading the page
      // or else we crash on iOS
      window.onbeforeunload = function() {
         closeHandlers = [];
         disconnect();
      };

      var url = (secure ? "wss:" : "ws:") + "//" + server + (port ? ":" + port : "") + "/sockets";
      console.log("Connecting to: " + url);
      socket = new WebSocket(url);
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

   function disconnect() {
      if (socket) {
         if (self.commsLog)
            console.log("Disconnecting... " + socket.readyState);
         var closeHack = (socket.readyState == WebSocket.CLOSING);
         if (closeHack) {
            // some sort of bug here when waking from sleep, it can take a very long long time
            // to transition from CLOSING to CLOSED and call our close handler, so we
            // take away the onclose handler and call it manually
            socket.onclose = null;
         }
         socket.close();
         if (self.commsLog)
            console.log("socket.close() called: " + socket.readyState);
         if (closeHack) { // call onSocketClose manually
            onSocketClose();
         }
      }
   }

   function logResponse(result) {
      var str = '[' + result._requestId + '] <- ' + nameOf(result) + ' ' + JSON.stringify(result, dropUnderscored);
      if (result.isError()) {
         console.warn(str);
      } else {
         console.debug(str);
      }
   }

   function logRequest(result, toId) {
      var toStr = toId == 0 ? " to any" : (toId == 1 ? " to direct" : " to " + toId);
      console.debug('[' + result._requestId + '] => ' + nameOf(result) + ' ' + JSON.stringify(result, dropUnderscored) + toStr);
   }

   function logMessage(result) {
      console.debug('[M:' + result._topicId + '] <- ' + nameOf(result) + ' ' + JSON.stringify(result, dropUnderscored));
   }

   function dropUnderscored(key, value) {
      if (typeof key == 'string' || key instanceof String) {
         if (key.indexOf("_") == 0)
            return undefined;
      }
      return value;
   }

   function handleResponse(result) {
      result.isError = function() {
         return result._contractId == 1 && result._structId == 1;
      };
      if (self.commsLog && result._requestId != keepAliveRequestId)
         logResponse(result);
      var func = requestHandlers[result._requestId];
      if (func) {
         func(result);
      }
      delete requestHandlers[result._requestId];
   }

   function handleMessage(result) {
      if (self.commsLog)
         logMessage(result);
      var func = messageHandlers[result._contractId + "." + result._structId];
      if (func) {
         func(result);
      }
   }

   // --- socket methods

   function onSocketOpen(event) {
      lastHeardFrom = Date.now();
      self.connected = true;
      if (self.commsLog)
         console.log("[socket] open: " + socket.URL);
      var i, array = openHandlers;
      for (i = 0; i < array.length; i++)
         array[i]();

      self.keepAlive = setInterval(function() {
         var elapsedHeard = Date.now() - lastHeardFrom;
         var elapsedSpoke = Date.now() - lastSpokeTo;
         if (elapsedSpoke > 6000) {
            // this keep alive is a backup
            sendDirect("KeepAlive", {});
         }
         if (elapsedHeard > 6000) {
            console.debug("We haven't heard from the server in " + elapsedHeard + " ms")
         }
         if (elapsedHeard > 20000) {
            disconnect();
         }
      }, 5000);

   }

   function onSocketClose(event) {
      self.connected = false;
      if (self.keepAlive != null) {
         clearInterval(self.keepAlive);
         self.keepAlive = null;
      }
      if (self.commsLog)
         console.log("[socket] closed")
      var i, array = closeHandlers;
      for (i = 0; i < array.length; i++)
         array[i]();

      // terminate all pending requests (only if using websockets I think...)      
      //      for ( var requestId in requestHandlers) {
      //         handleResponse(7);
      //      }
   }

   function onSocketMessage(event) {
      lastHeardFrom = Date.now();
      var result = null;
      if (event.data.indexOf("{") == 0) {
         result = JSON.parse(event.data);
      } else {
         // TODO figure out how to communicate errors
         result = parseInt(event.data);
      }
      if (result._requestId != null) {
         handleResponse(result);
      } else if (result._topicId != null) {
         handleMessage(result);
      }
   }

   function onSocketError(event) {
      if (self.commsLog)
         console.log("[socket] error: " + JSON.stringify(event));
   }

   function isKeepAlive(contractId, structId) {
      return (contractId == 1 && structId == 5512920) || (contractId == 10 && structId == 15966706);
   }

   // ------------------------ long polling fall-back ----------------------------- //

   function startPollingSession(host, secure) {
      self.polling = true;
      self.pollPending = false;
      self.connected = true;
      self.entityInfo = null;
      lastHeardFrom = Date.now();
      if (self.commsLog)
         console.log("[poller] open: " + host);
      return {
         listen : function(onOpen, onClose) {
            onOpen();
         }
      }
   }

   function sendRPC(data) {
      if (self.entityInfo != null) {
         data._token = self.entityInfo.token;
      }
      //console.debug("SEND RPC: " + JSON.stringify(data));
      $.ajax({
         type : "POST",
         url : "/poll",
         timeout : 24000,
         data : JSON.stringify(data),
         dataType : 'json',
         success : function(data) {
            self.connected = true;
            handleResponse(data);
            schedulePoll(100);
         },
         error : function(XMLHttpRequest, textStatus, errorThrown) {
            console.error(textStatus + " (" + errorThrown + ")");
            if (textStatus == 'timeout') {
               handleResponse(makeError(data._requestId, 3));
            } else {
               handleResponse(makeError(data._requestId, 1));
            }
            onSocketClose(); // do a fake socket close event
         }
      });
   }

   function schedulePoll(millis) {
      setTimeout(function() {
         poll()
      }, millis);
   }

   function poll() {
      if (!self.polling)
         return;
      if (self.entityInfo != null && self.entityInfo.entityId != 0) {
         if (self.pollPending == false) {
            var data = {
               _token : self.entityInfo.token
            };
            console.debug("POLL -> " + JSON.stringify(data));
            self.pollPending = true;
            $.ajax({
               type : "POST",
               url : "/poll",
               timeout : 12000,
               data : JSON.stringify(data),
               dataType : 'json',
               success : function(data) {
                  self.connected = true;
                  self.pollPending = false;
                  if (data.error) {
                     console.debug("POLL <- ERROR: " + data.error);
                     schedulePoll(1000);
                  } else {
                     console.debug("POLL <- " + data.messages.length + " items");
                     $.each(data.messages, function(i, m) {
                        handleMessage(m)
                     });
                     schedulePoll(100);
                  }
               },
               error : function(XMLHttpRequest, textStatus, errorThrown) {
                  console.error(textStatus + " (" + errorThrown + ")");
                  self.pollPending = false;
                  onSocketClose(); // fake socket close event 
               }
            });
         }
      }
   }

}
