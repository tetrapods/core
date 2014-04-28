package io.tetrapod.core.rpc;

import io.tetrapod.core.Session;
import io.tetrapod.protocol.core.RequestHeader;

public class SessionRequestContext extends RequestContext {

   public final Session       session;

   public SessionRequestContext(RequestHeader header, Session session) {
      super(header);
      this.session = session;
   }
   
   // security turned off for now pending resolution of admin accounts
   
   @Override
   public Response securityCheck(Request request) {
      return null;
   }
   
   @Override
   public Response securityCheck(Request request, int accountId, String authToken) {
      return null;
   }

   // sketch using identity to store admin accounts, but we probably don't want to go that way

   
// public Response securityCheck(RequestContext ctx) {
// Security mine = getSecurity();
// Security theirs = ctx.getSenderSecurity();
// if (theirs.ordinal() < mine.ordinal())
//    return new Error(Core.ERROR_INVALID_RIGHTS);
// return null;
//}
//
//protected Response securityCheck(int accountId, String authToken, RequestContext ctx) {
// Value<Integer> error = new Value<>(Core.ERROR_INVALID_RIGHTS);
// Security mine = getSecurity();
// Security theirs = ctx.getSenderSecurity(accountId, authToken, error);
// if (theirs.ordinal() < mine.ordinal())
//    return new Error(error.get());
// return null;
//}   
//   public Security getSenderSecurity() {
//      if (header.fromType == Core.TYPE_TETRAPOD || header.fromType == Core.TYPE_SERVICE)
//         return Security.INTERNAL;
//      if (header.fromType == Core.TYPE_ADMIN)
//         return Security.ADMIN;
//      return Security.PUBLIC; 
//   }
//
//   public Security getSenderSecurity(int accountId, String authToken, Value<Integer> errorCode) {
//      if (header.fromType == Core.TYPE_TETRAPOD || header.fromType == Core.TYPE_SERVICE)
//         return Security.INTERNAL;
//      Decoded d = AuthToken.decodeUserToken(authToken, accountId, header.fromId);
//      if (d != null && d.timeLeft >= 0) {
//         int perms = d.miscValues[0];
//         int anyAdmin = User.PROPS_ADMIN_T1 | User.PROPS_ADMIN_T2 | User.PROPS_ADMIN_T3 | User.PROPS_ADMIN_T4; 
//         if ((perms & anyAdmin) != 0) {
//            // to get more detailed admin callers will have to decode auth token themselves
//            return Security.ADMIN;
//         }
//         return Security.PROTECTED;
//      } else {
//         if (d.timeLeft < 0) {
//            errorCode.set(ERROR_RIGHTS_EXPIRED);
//         } else {
//            errorCode.set(ERROR_UNKNOWN);
//         }
//         return Security.PUBLIC;
//      }
//   }
}
