package io.tetrapod.core.rpc;

import io.tetrapod.protocol.core.RequestHeader;

public class InternalRequestContext extends SessionRequestContext {
   private final ResponseHandler handler;

   public InternalRequestContext(RequestHeader header, ResponseHandler handler) {
      super(header, null);
      this.handler = handler;
   }

   @Override
   public void handlePendingResponse(Response res, int originalRequestId) {
      handler.onResponse(res);
   }

}
