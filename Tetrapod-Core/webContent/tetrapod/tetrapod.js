var TP = TP || {};

TP.requestCounter = 0;

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
   
   args.num = TP.requestCounter++;
   args.contractId = val.contractId;
   args.structId = val.structId;
   args.toId = toId;
   
   console.log("sending request: " + JSON.stringify(args, null, 3));
   
   // TODO: actually send the request
   // TODO: return an object with a handle(func) method that will add func as the 
   //       callback for this request
}