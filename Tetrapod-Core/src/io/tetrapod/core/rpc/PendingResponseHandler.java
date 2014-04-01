package io.tetrapod.core.rpc;

/**
 * A pending response handler lets us chain RPC calls together, and derive a final response from another response
 */
public interface PendingResponseHandler {

   public Response onResponse(Response res);

}
