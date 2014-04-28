package io.tetrapod.core.rpc;

import io.tetrapod.protocol.core.RequestHeader;

abstract public class RequestContext {
   
   public final RequestHeader header;
   
   public RequestContext(RequestHeader header) {
      this.header = header;
   }
   
   abstract public Response securityCheck(Request request, int accountId, String authToken);

   abstract public Response securityCheck(Request request);


   
//   public Response securityCheck(RequestContext ctx) {
//    Security mine = getSecurity();
//    Security theirs = ctx.getSenderSecurity();
//    if (theirs.ordinal() < mine.ordinal())
//       return new Error(Core.ERROR_INVALID_RIGHTS);
//    return null;
// }
// 
// protected Response securityCheck(int accountId, String authToken, RequestContext ctx) {
//    Value<Integer> error = new Value<>(Core.ERROR_INVALID_RIGHTS);
//    Security mine = getSecurity();
//    Security theirs = ctx.getSenderSecurity(accountId, authToken, error);
//    if (theirs.ordinal() < mine.ordinal())
//       return new Error(error.get());
//    return null;
// }
   
}
