package io.tetrapod.core.rpc;

import io.tetrapod.core.tasks.Task;
import io.tetrapod.core.utils.CoreUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author paulm
 *         Created: 8/29/16
 */
public abstract class TaskRequestWithResponse<T extends Response> extends RequestWithResponse<T> implements TaskDispatcher {
   private static final Logger logger = LoggerFactory.getLogger(TaskRequestWithResponse.class);

   @Override
   public Response dispatch(ServiceAPI is, RequestContext ctx) {
      throw new AbstractMethodError("should be calling dispatchTask");
   }

   public Task<? extends Response> dispatchTask(ServiceAPI is, RequestContext ctx) {
      return CoreUtil.cast(Task.from(is.genericRequest(this, ctx)));
   }

}
