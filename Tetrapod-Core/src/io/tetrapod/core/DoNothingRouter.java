package io.tetrapod.core;

import io.tetrapod.core.rpc.Async;
import io.tetrapod.core.rpc.AsyncSequence;
import io.tetrapod.core.rpc.PendingResponseHandler;
import io.tetrapod.core.rpc.Request;
import io.tetrapod.core.rpc.RequestContext;
import io.tetrapod.core.rpc.RequestWithResponse;
import io.tetrapod.core.rpc.Response;
import io.tetrapod.core.rpc.ResponseHandler;
import io.tetrapod.core.tasks.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default router that just throws exceptions.  All services that require routing by using the @route
 * annotation in codegen must provide a real router
 * @author paulm
 *         Created: 2/6/17
 */
public class DoNothingRouter implements RequestRouter {

   @Override
   public Response sendPending(Request request, int id, RequestContext ctx) {
      throw new IllegalStateException("service is not capable of routing requests");
   }

   @Override
   public Response sendPending(Request request, int id, PendingResponseHandler handler) {
      throw new IllegalStateException("service is not capable of routing requests");
   }

   @Override
   public Response sendAndWait(Request request, int id) {
      throw new IllegalStateException("service is not capable of routing requests");
   }

   @Override
   public Response sendAndWait(Request request, int id, int startingHostId) {
      throw new IllegalStateException("service is not capable of routing requests");
   }

   @Override
   public Async send(Request request, int id) {
      throw new IllegalStateException("service is not capable of routing requests");
   }

   @Override
   public Async send(Request request, int id, Async.IResponseHandler handler) {
      throw new IllegalStateException("service is not capable of routing requests");
   }

   @Override
   public <TResp extends Response> Task<TResp> sendTask(Request request, int id) {
      throw new IllegalStateException("service is not capable of routing requests");
   }

   @Override
   public <TResp extends Response> Task<TResp> sendTask(RequestWithResponse<TResp> request, int id) {
      throw new IllegalStateException("service is not capable of routing requests");
   }

   @Override
   public Async send(Request request, int id, AsyncSequence seq, Async.IResponseHandlerErr handler) {
      throw new IllegalStateException("service is not capable of routing requests");
   }

   @Override
   public Async send(Request request, int id, ResponseHandler handler) {
      throw new IllegalStateException("service is not capable of routing requests");
   }

   @Override
   public Async send(Request request, int id, int hostId, int retries, ResponseHandler handler) {
      throw new IllegalStateException("service is not capable of routing requests");
   }

   @Override
   public Response sendPending(Request request, int id, int hostId, int retries, PendingResponseHandler handler) {
      throw new IllegalStateException("service is not capable of routing requests");
   }
}
