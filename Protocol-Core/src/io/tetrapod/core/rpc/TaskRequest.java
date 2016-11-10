package io.tetrapod.core.rpc;

import io.tetrapod.core.tasks.Task;

public abstract class TaskRequest extends Request implements TaskDispatcher {

   @Override
   public Response dispatch(ServiceAPI is, RequestContext ctx) {
      throw new AbstractMethodError("should be calling dispatchTask");
   }

   public Task<? extends Response> dispatchTask(ServiceAPI is, RequestContext ctx) {
      return Task.from(is.genericRequest(this, ctx));
   }
}
