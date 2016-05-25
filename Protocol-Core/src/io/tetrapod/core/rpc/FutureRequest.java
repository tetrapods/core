package io.tetrapod.core.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * @author paulm
 *         Created: 5/24/16
 */
public abstract class FutureRequest extends Request {
   private static final Logger logger = LoggerFactory.getLogger(FutureRequest.class);

   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      throw new AbstractMethodError("should be calling dispatchFuture");
   }



   public CompletableFuture<? extends Response> dispatchFuture(ServiceAPI is, RequestContext ctx) {
      return CompletableFuture.completedFuture(is.genericRequest(this, ctx));
   }

}
