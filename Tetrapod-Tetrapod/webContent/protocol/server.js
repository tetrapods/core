define([], function() {
   return TP_Server;
});

function TP_Server() {
   var self = this;

   // private vars
   var protocol = {
      request: {},
      response: {},
      struct: {},
      message: {},
      consts: {},
      reverseMap: {},
      errors: {}
   };
   var requestCounter = 0;
   var requestContexts = {};
   var messageHandlers = [];
   var openHandlers = [];
   var closeHandlers = [];
   var securityErrorHandlers = [];
   var socket;
   var lastHeardFrom = 0;
   var lastSpokeTo = 0;

   // public interface
   self.forceLongPolling = false;
   self.commsLog = false;
   self.commsLogKeepAlives = false;
   self.register = register;
   self.registerFlag = registerFlag;
   self.addMessageHandler = addMessageHandler;
   self.removeMessageHandler = removeMessageHandler;
   self.getErrorStrings = getErrorStrings;
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
   self.getRequestContract = getRequestContract;

   for (var i = 0; i < arguments.length; i++) {
      var p = new arguments[i](self);
      registerErrors(p[p.name].error);
      protocol.consts[p.name] = p[p.name];
   }

   function register(type, contractName, structName, contractId, structId) {
      var map = protocol[type];
      var val = {
         contractId: contractId,
         structId: structId
      };
      map[contractName + "." + structName] = val;
      if (map[structName]) {
         map[structName] = 0;
      } else {
         map[structName] = val;
      }
      protocol.reverseMap["" + contractId + "." + structId] = contractName + "." + structName;
   }

   function registerErrors(errors) {
      if (errors) {
         for ( var constName in errors) {
            if (errors.hasOwnProperty(constName)) {
               protocol.errors[errors[constName]] = constName;
            }
         }
      }
   }

   function registerFlag(f) {
      if (f) {
         f.on = function(val) {
            return {
               value: val,
               parent: f,
               isAnySet: function() {
                  return (toFlags(arguments, this.parent) & this.value) != 0;
               },
               isSet: function() {
                  var flags = toFlags(arguments, this.parent);
                  return (flags & this.value) == flags;
               },
               isNoneSet: function() {
                  return (toFlags(arguments, this.parent) & this.value) == 0;
               },
               set: function() {
                  this.value = this.value | toFlags(arguments, this.parent);
                  return this;
               },
               unset: function() {
                  this.value = this.value & ~toFlags(arguments, this.parent);
                  return this;
               },
            };
         };
      }
   }

   function toFlags(args, parent) {
      var flags = 0;
      for (var i = 0; i < args.length; i++) {
         if (typeof args[i] === "number")
            flags = flags | args[i];
         else if (typeof args[i] === "string")
            flags = flags | parent[args[i]];
      }
      return flags;
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
      var list = messageHandlers[val.contractId + "." + val.structId];
      if (!list) {
         messageHandlers[val.contractId + "." + val.structId] = list = [];
      }
      list.push(handler);
   }

   function removeMessageHandler(message, handler) {
      var val = protocol.message[message];
      if (!val) {
         console.log("unknown message: " + message);
         return;
      }
      if (val === 0) {
         console.log("ambiguous message: " + message);
         return;
      }
      var list = messageHandlers[val.contractId + "." + val.structId];
      if (list) {
         var ix = list.indexOf(handler);
         if (ix >= 0)
            list.splice(ix, 1);
      }
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

   function getRequestContract(request) {
      var val = protocol.request[request];
      if (!val) {
         console.log("unknown request: " + request);
         return;
      }
      if (val === 0) {
         console.log("ambiguous request: " + request);
         return;
      }
      var name = nameOf(val.contractId, val.structId);
      var ix = name.indexOf('.');
      return name.substring(0, ix);
   }

   // dispatch a request
   function sendRequest(contractId, structId, args, toId, requestHandler) {
      toId = typeof toId !== 'undefined' ? toId : 0;
      var requestId = requestCounter++;
      args._requestId = requestId;
      args._contractId = contractId;
      args._structId = structId;
      args._toId = toId;

      logRequest(args, toId);

      requestContexts[requestId] = {
         request: args,
         handler: requestHandler
      };

      if (self.polling) {
         lastSpokeTo = Date.now();
         sendRPC(args);
      } else {
         if (isConnected()) {
            var data = JSON.stringify(args, null, 3);
            if (data.length < 1024 * 128) {
               lastSpokeTo = Date.now();
               socket.send(data);

               // triggers a timeout in 35 seconds, if we don't get a response 
               setTimeout(function() {
                  if (requestContexts[requestId]) {
                     handleResponse(makeError(requestId, 3)); // TIMEOUT
                  }
               }, 35000);

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
         _requestId: requestId,
         _contractId: 1,
         _structId: 1,
         errorCode: code
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
         listen: function(onOpen, onClose, onSecurity) {
            if (onOpen) {
               openHandlers = [onOpen];
            }
            if (onClose) {
               closeHandlers = [onClose];
            }
            if (onSecurity) {
               securityErrorHandlers = [onSecurity];
            }
         }
      }
   }

   function disconnect() {
      if (socket) {
         commslog("Disconnecting... " + socket.readyState);
         var closeHack = (socket.readyState == WebSocket.CLOSING);
         if (closeHack) {
            // some sort of bug here when waking from sleep, it can take a very long long time
            // to transition from CLOSING to CLOSED and call our close handler, so we
            // take away the onclose handler and call it manually
            socket.onclose = null;
         }
         socket.close();
         commslog("socket.close() called: " + socket.readyState);
         if (closeHack) { // call onSocketClose manually
            onSocketClose();
         }
      }
   }

   function logResponse(result, req) {
      if (self.commsLog) {
         if (!isKeepAlive(req._contractId, req._structId) || self.commsLogKeepAlives) {
            var str = logstamp() + ' [' + result._requestId + '] <- ' + nameOf(result) + ' ' + JSON.stringify(result, dropUnderscored);
            if (result.isError()) {
               if (result.errorCode != protocol.consts.Core.error.NOT_CONFIGURED) {
                  var err = getErrorStrings(result.errorCode);
                  err = err ? (" " + err + " ") : "";
                  console.warn(str + err);
               } else {
                  str = logstamp() + ' [' + result._requestId + '] <- TETRAPOD.CONFIGURED_MSG ' + JSON.stringify(result, dropUnderscored) + ' Not Configured Message';
                  console.debug(str);
               }
            } else {
               console.debug(str);
            }
         }
      }
   }

   function getErrorStrings(code) {
      return protocol.errors[code];
   }

   function logRequest(req, toId) {
      if (self.commsLog) {
         if (!isKeepAlive(req._contractId, req._structId) || self.commsLogKeepAlives) {
            var toStr = toId == 0 ? " to any" : (toId == 1 ? " to direct" : " to " + toId);
            try {
               console.debug(logstamp() + ' [' + req._requestId + '] => ' + nameOf(req) + ' ' + JSON.stringify(req, dropUnderscored) + toStr);
            } catch (e) {
               console.error('Error logging a request: ' + e);
               console.error(req);
            }
         }
      }
   }

   function logMessage(result) {
      if (self.commsLog) {
         console.debug(logstamp() + ' [M:' + result._topicId + '] <- ' + nameOf(result) + ' ' + JSON.stringify(result, dropUnderscored));
      }
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
      var ctx = requestContexts[result._requestId];
      if (!ctx) {
         console.warn("Got unexpected result");
         console.warn(result);
         return;
      }

      logResponse(result, ctx.request);

      if (ctx.handler) {
         ctx.handler(result);
      }
      if (result.isError() && result.errorCode == 10) {
         // 10 is RIGHTS_EXPIRED
         for (var i = 0; i < securityErrorHandlers.length; i++) {
            securityErrorHandlers[i](result);
         }
      }
      delete requestContexts[result._requestId];
   }

   function handleMessage(result) {
      logMessage(result);
      var list = messageHandlers[result._contractId + "." + result._structId];
      if (list) {
         for (var i = 0; i < list.length; i++) {
            list[i](result);
         }
      }
   }

   // --- socket methods

   function onSocketOpen(event) {
      lastHeardFrom = Date.now();
      self.connected = true;
      commslog("[socket] open: " + socket.url);
      var i, array = openHandlers;
      for (i = 0; i < array.length; i++)
         array[i]();

      self.keepAlive = setInterval(function() {
         var elapsedHeard = Date.now() - lastHeardFrom;
         var elapsedSpoke = Date.now() - lastSpokeTo;
         if (elapsedSpoke > 6000) {
            // this keep alive is a backup
            sendDirect("Web.KeepAlive", {});
         }
         if (elapsedHeard > 6000) {
            commslog("We haven't heard from the server in " + elapsedHeard + " ms")
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
      commslog("[socket] closed")
      var i, array = closeHandlers;
      for (i = 0; i < array.length; i++)
         array[i]();

      // terminate all pending requests (only if using websockets I think...)      
      //      for ( var requestId in requestContexts) {
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

   function pad(pad, str) {
      str = str.toString();
      while (str.length < pad) {
         str = '0' + str;
      }
      return str;
   }

   function logstamp() {
      var now = new Date();
      var p2 = '00';
      return pad(2, now.getHours()) + ':' + pad(2, now.getMinutes()) + ':' + pad(2, now.getSeconds()) + '.' + pad(3, now.getMilliseconds());
   }

   function commslog(msg) {
      if (self.commsLog)
         console.log(logstamp() + ' ' + msg);
   }

   function onSocketError(event) {
      commslog("[socket] error: " + JSON.stringify(event));
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
      commslog("[poller] open: " + host);
      return {
         listen: function(onOpen, onClose) {
            onOpen();
         }
      }
   }

   function sendRPC(data) {
      if (self.entityInfo != null) {
         data._token = self.entityInfo.token;
      }
      //console.debug("SEND RPC: " + JSON.stringify(data));
      /* global $ */
      $.ajax({
         type: "POST",
         url: "/poll",
         timeout: 24000,
         data: JSON.stringify(data),
         dataType: 'json',
         success: function(data) {
            self.connected = true;
            handleResponse(data);
            schedulePoll(100);
         },
         error: function(XMLHttpRequest, textStatus, errorThrown) {
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
               _token: self.entityInfo.token
            };
            console.debug("POLL -> " + JSON.stringify(data));
            self.pollPending = true;
            $.ajax({
               type: "POST",
               url: "/poll",
               timeout: 12000,
               data: JSON.stringify(data),
               dataType: 'json',
               success: function(data) {
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
               error: function(XMLHttpRequest, textStatus, errorThrown) {
                  console.error(textStatus + " (" + errorThrown + ")");
                  self.pollPending = false;
                  onSocketClose(); // fake socket close event 
               }
            });
         }
      }
   }

}
