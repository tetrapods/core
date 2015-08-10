package io.tetrapod.core.rpc;

/**
 * Allows executing an async task and then responding to a pending request
 */
public class AsyncResponder {
   private final SessionRequestContext ctx;

   public AsyncResponder(RequestContext ctx) {
      this.ctx = (SessionRequestContext) ctx;
   }

   public void respondWith(Response res) {
      if (ctx.session != null) {
         ctx.session.sendResponse(res, ctx.header.requestId);
      }
   }

}
