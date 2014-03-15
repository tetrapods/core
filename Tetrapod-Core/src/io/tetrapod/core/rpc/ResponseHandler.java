package io.tetrapod.core.rpc;

public interface ResponseHandler {
   public void onResponse(Response res, int errorCode);
}
