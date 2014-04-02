package io.tetrapod.core.rpc;

import io.tetrapod.core.utils.Value;
import io.tetrapod.protocol.core.Core;

abstract public class Request extends Structure {

   public Response dispatch(ServiceAPI is, RequestContext ctx) {
      return is.genericRequest(this, ctx);
   }
   
   public Response securityCheck(RequestContext ctx) {
//      Security mine = getSecurity();
//      Security theirs = ctx.getSenderSecurity();
//      if (theirs.ordinal() < mine.ordinal())
//         return new Error(Core.ERROR_INVALID_RIGHTS);
      return null;
   }
   
   protected Response securityCheck(int accountId, String authToken, RequestContext ctx) {
//      Value<Integer> error = new Value<>(Core.ERROR_INVALID_RIGHTS);
//      Security mine = getSecurity();
//      Security theirs = ctx.getSenderSecurity(accountId, authToken, error);
//      if (theirs.ordinal() < mine.ordinal())
//         return new Error(error.get());
      return null;
   }
   

}
