package io.tetrapod.core.rpc;

import io.tetrapod.core.tasks.Task;

/**
 * @author paulm
 *         Created: 8/29/16
 */
public interface TaskDispatcher {
   Task<? extends Response> dispatchTask(ServiceAPI is, RequestContext ctx);
}
