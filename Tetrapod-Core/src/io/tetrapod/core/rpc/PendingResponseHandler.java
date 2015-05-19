package io.tetrapod.core.rpc;

import io.tetrapod.core.Session;

/**
 * A pending response handler lets us chain RPC calls together, and derive a final response from another response
 */
public abstract class PendingResponseHandler {

   public final int originalRequestId;
   public final Session session;   
   
   public PendingResponseHandler(RequestContext ctx) {
      originalRequestId = ctx.header.requestId;
      if (ctx instanceof SessionRequestContext) {
         session = ((SessionRequestContext) ctx).session;
      } else {
         session = null;
      }
   }

   public PendingResponseHandler(PendingResponseHandler handler) {
      originalRequestId = handler.originalRequestId;
      session = handler.session;
   }

   public PendingResponseHandler(int originalRequestId) {
      this.originalRequestId = originalRequestId;
      session = null;
   }

   abstract public Response onResponse(Response res);

}
