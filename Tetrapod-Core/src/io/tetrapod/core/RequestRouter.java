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

/**
 * This interface allows DefaultService to have knowledge of Routing, without having to move all our
 * request routing plumbing over to core.
 * @author paulm
 *         Created: 2/6/17
 */
public interface RequestRouter {
   Response sendPending(Request request, int id, RequestContext ctx);

   Response sendPending(Request request, int id, PendingResponseHandler handler);

   Response sendAndWait(Request request, int id);

   Response sendAndWait(Request request, int id, int startingHostId);

   Async send(Request request, int id);

   Async send(Request request, int id, Async.IResponseHandler handler);

   <TResp extends Response> Task<TResp> sendTask(Request request, int id);

   <TResp extends Response> Task<TResp> sendTask(RequestWithResponse<TResp> request, int id);

   Async send(Request request, int id, AsyncSequence seq, Async.IResponseHandlerErr handler);

   Async send(Request request, int id, ResponseHandler handler);

   Async send(Request request, int id, int hostId, int retries, ResponseHandler handler);

   Response sendPending(Request request, int id, int hostId, int retries, PendingResponseHandler handler);
}
