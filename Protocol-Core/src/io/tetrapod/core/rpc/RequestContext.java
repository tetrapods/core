package io.tetrapod.core.rpc;

import io.tetrapod.protocol.core.RequestHeader;

abstract public class RequestContext {
   
   public final RequestHeader header;
   
   public RequestContext(RequestHeader header) {
      this.header = header;
   }
   
   abstract public Response securityCheck(Request request, int accountId, String authToken);

   abstract public Response securityCheck(Request request);
   
}
