package io.tetrapod.core.rpc;

import io.tetrapod.protocol.core.Entity;

public interface RequestSender {

   Async sendRequest(Request request, int hostId);

   Response sendPendingRequest(Request request, int hostId, PendingResponseHandler pendingResponseHandler);

   Async sendRequest(Request req);

   Response sendPendingRequest(Request req, PendingResponseHandler pendingResponseHandler);

   boolean isServiceExistant(int hostId);

   Entity getRandomAvailableService(int contractId);
}
