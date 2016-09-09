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
      assert res != Response.PENDING;
      ctx.handlePendingResponse(res, ctx.header);
   }

}
