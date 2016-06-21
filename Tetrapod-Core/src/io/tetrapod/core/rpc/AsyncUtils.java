package io.tetrapod.core.rpc;

import io.tetrapod.core.tasks.Task;


/**
 * @author paulm
 *         Created: 6/3/16
 */
public class AsyncUtils {

   /**
    * Provides a single consistent way to handle all
    * @param future
    * @param resp
    */
   public static void handleFuture(Task<Response> future, Response resp) {
      if (resp.isError()) {
         future.completeExceptionally(new ErrorResponseException(resp.errorCode()));
      } else {
         future.complete(resp);
      }
   }
}
