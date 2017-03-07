package io.tetrapod.core.rpc;

public interface ServiceAPI {
   default Response genericRequest(Request r, RequestContext ctx) { return null; }
}
