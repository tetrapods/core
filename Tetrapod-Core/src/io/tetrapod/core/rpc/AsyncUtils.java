package io.tetrapod.core.rpc;

import io.tetrapod.core.StructureFactory;
import io.tetrapod.core.tasks.Task;
import io.tetrapod.core.utils.Util;


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
   public static void handleTask(Task<? extends Response> future, Request req, Response resp) {
      if (resp.isError()) {
         future.completeExceptionally(new ErrorResponseException(resp.errorCode(), "Request: " + StructureFactory.getName(req.getContractId(), req.getStructId())));
      } else {
         future.complete(Util.cast(resp));
      }
   }
}
