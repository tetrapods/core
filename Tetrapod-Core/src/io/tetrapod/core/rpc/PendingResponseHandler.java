package io.tetrapod.core.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.tetrapod.core.Session;

/**
 * A pending response handler lets us chain RPC calls together, and derive a final response from another response
 */
public abstract class PendingResponseHandler {

   public static final Logger  logger = LoggerFactory.getLogger(PendingResponseHandler.class);

   public final RequestContext context;
   public final Session        session;

   public PendingResponseHandler(RequestContext ctx) {
      this.context = ctx;
      if (ctx instanceof SessionRequestContext) {
         this.session = ((SessionRequestContext) ctx).session;
      } else {
         this.session = null;
      }
   }

   public PendingResponseHandler(PendingResponseHandler handler) {
      this.session = handler.session;
      this.context = handler.context;
   }

   abstract public Response onResponse(Response res);

   // return the response we were pending on
   public boolean sendResponse(Response pendingRes) {
      assert pendingRes != Response.PENDING;
      if (context != null) {
         context.handlePendingResponse(pendingRes, context.header);
         return true;
      } else if (session != null) {
         session.sendResponse(pendingRes, context.header);
         return true;
      } else {
         return false;
      }
   }

}
