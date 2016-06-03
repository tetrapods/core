package io.tetrapod.core.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import static io.tetrapod.protocol.core.CoreContract.ERROR_UNKNOWN;

/**
 * @author paulm
 *         Created: 6/3/16
 */
public class AsyncUtils {
   private static final Logger logger = LoggerFactory.getLogger(AsyncUtils.class);

   /**
    * Provides a single consistent way to handle all
    * @param future
    * @param resp
    */
   public static void handleFuture(CompletableFuture<Response> future, Response resp) {
      if (resp.isError()) {
         future.completeExceptionally(new ErrorResponseException(resp.errorCode()));
      } else {
         future.complete(resp);
      }
   }
}
