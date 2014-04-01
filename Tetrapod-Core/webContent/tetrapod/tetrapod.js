var TP = TP || {};

TP.requestCounter = 0;

TP.requestHandlers = [];
TP.messageHandlers = [];

TP.register = function(type, contractName, structName, contractId, structId) {
	var map = TP.protocol[type];
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
}

TP.registerConst = function(contractName, structName, constName, constValue) {
   var map = TP.protocol["consts"];
   var o = map[contractName + "." + structName] || {};
   map[contractName + "." + structName] = o;
   o[constName] = constValue;
}

TP.addMessageHandler = function(message, handler) {
   var val = TP.protocol.message[message];
   if (!val) {
      console.log("unknown message: " + message);
      return;
   }
   if (val === 0) {
      console.log("ambiguous message: " + message);
      return;
   }
	TP.messageHandlers[val.contractId + "." + val.structId] = handler;
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
		handle : function(func) {
			TP.requestHandlers[requestId] = func;
		}
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
		var i;
		var array = socket.openListeners;
		for (i = 0; i < array.length; i++)
			array[i]();
	};
	socket.onmessage = function(event) {
		//console.log("[socket] received: " + event.data);
		var result = null; 
		if (event.data.indexOf("{") == 0) {
			result = JSON.parse(event.data);
		} else {
			// TODO figure out how to communicate errors
			result = parseInt(event.data);
		}
		if (result._requestId != null) {
			var func = TP.requestHandlers[result._requestId];
			if (func) {
				result.isError = function() { return result._contractId == 1 && result._structId == 1; };				
				func(result);
			}
		} else if (result._topicId != null) {
			console.log("MESSAGE: " + JSON.stringify(result))
			var func = TP.messageHandlers[result._contractId+"."+result._structId];
			if (func) {
				func(result);
			}
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
		//console.log("[socket] send: " + data)
		socket.privsend(data);
	}
	TP.socket = socket;
	return {
		onOpen : function(func) {
			socket.openListeners.push(func);
		}
	}
}

TP.logResponse = function(result) {
	if (result.isError()) {
		console.log("RESULT: ERROR " + result.errorCode)
	} else {
		console.log("RESULT: " + JSON.stringify(result))
	}
}
