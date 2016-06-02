package io.tetrapod.core.rpc;


abstract public class Request<TResp extends Response> extends Structure {

   public Response dispatch(ServiceAPI is, RequestContext ctx) {
      return is.genericRequest(this, ctx);
   }

   public Response securityCheck(RequestContext ctx) {
      return ctx.securityCheck(this);
   }

   public ResponseWrapper<TResp> getResponseWrapper(TResp resp) {
      return new ResponseWrapper<TResp>(resp);
   }

   public ResponseWrapper<TResp> getResponseWrapper(Integer errorCode) {
      return new ResponseWrapper<TResp>(errorCode);
   }

}
