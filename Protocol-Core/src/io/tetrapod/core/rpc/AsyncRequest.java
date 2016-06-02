package io.tetrapod.core.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * @author paulm
 *         Created: 5/24/16
 */
public abstract class AsyncRequest extends Request {
   private static final Logger logger = LoggerFactory.getLogger(AsyncRequest.class);

   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      throw new AbstractMethodError("should be calling dispatchAsync");
   }



   public CompletableFuture<? extends Response> dispatchAsync(ServiceAPI is, RequestContext ctx) {
      return CompletableFuture.completedFuture(is.genericRequest(this, ctx));
   }

}
