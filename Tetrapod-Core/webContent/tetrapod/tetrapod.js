var TP = TP || {};

TP.requestCounter = 0;

TP.requestHandlers = [];

TP.register = function(type, contractName, structName, contractId, structId) {
   var map = TP.protocol[type];
   var val = { contractId: contractId, structId: structId };
   map[contractName + "." + structName] = val;
   if (map[structName]) {
      map[structName] = 0;
   } else {
      map[structName] = val;
   }
}

TP.send = function(request, args, toId) {
   var val = TP.protocol.request[request];
   toId = typeof toId !== 'undefined' ? toId : 0;
   
   if (!val) {
      console.log("unknown request: " + request);
      return;
   }
   
   if (val === 0) {
      console.log("ambiguous request: " + request);
      return;
   }
   
   var requestId = TP.requestCounter++;
   args._requestId = requestId;
   args._contractId = val.contractId;
   args._structId = val.structId;
   args._toId = toId;
   
   if (TP.socket && TP.socket.readyState == WebSocket.OPEN) {
      TP.socket.send(JSON.stringify(args, null, 3))
   }
   
   return {
      handle: function(func) { TP.requestHandlers[requestId] = func; }
   }
}

TP.connect = function(server, port) {
   port = typeof port !== 'undefined' ? port : 9903;
   if (!window.WebSocket) {
      window.WebSocket = window.MozWebSocket;
   }
   if (!window.WebSocket) {
      return null; 
   }
   var socket = new WebSocket("ws://" + server + ":" + port + "/sockets");
   socket.openListeners = [];
   socket.onopen = function(event) {
      console.log("[socket] open")
      var i; var array = socket.openListeners;
      for (i = 0; i < array.length; i++)
         array[i]();
   };
   socket.onmessage = function(event) {
      console.log("[socket] received: " + event.data);
      var result = null;
      var errorCode = 0;
      if (event.data.indexOf("{") == 0) {
         result = JSON.parse(event.data);
      } else {
         // TODO figure out how to communicate errors
         errorCode = parseInt(event.data);
      }
      var func = TP.requestHandlers[result._requestId];
      if (func) {
         func(result, errorCode);
      }
   };
   socket.onclose = function(event) {
      console.log("[socket] closed")
   }
   socket.onerror = function(event) {
      console.log("[socket] error")
   }
   socket.privsend = socket.send;
   socket.send = function(data) {
      console.log("[socket] send: " + data)
      socket.privsend(data);
   }
   TP.socket = socket;
   return {
      onOpen: function(func) { socket.openListeners.push(func); }
   }
};
