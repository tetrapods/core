package io.tetrapod.core.rpc;

public interface RequestSender {

   Async sendRequest(Request request, int hostId);

   Response sendPendingRequest(Request request, int hostId, PendingResponseHandler pendingResponseHandler);

   Async sendRequest(Request req);

   Response sendPendingRequest(Request req, PendingResponseHandler pendingResponseHandler);

   boolean isServiceExistant(int hostId);

}
