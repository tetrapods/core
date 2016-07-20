package io.tetrapod.core.rpc;


abstract public class Request extends Structure {

   public Response dispatch(ServiceAPI is, RequestContext ctx) {
      return is.genericRequest(this, ctx);
   }

   public Response securityCheck(RequestContext ctx) {
      return ctx.securityCheck(this);
   }

}
