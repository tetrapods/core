package io.tetrapod.core.rpc;

public interface ServiceAPI {

   Response genericRequest(Request r, RequestContext ctx);

}
