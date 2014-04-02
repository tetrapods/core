package io.tetrapod.core.rpc;

/**
 * A pending response handler lets us chain RPC calls together, and derive a final response from another response
 */
public abstract class PendingResponseHandler {

   public final int originalRequestId;
   
   public PendingResponseHandler(RequestContext ctx) {
      originalRequestId = ctx.header.requestId;
   }
   
   abstract public Response onResponse(Response res);

}
