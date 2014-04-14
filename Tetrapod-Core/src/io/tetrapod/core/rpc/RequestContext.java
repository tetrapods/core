package io.tetrapod.core.rpc;

import io.tetrapod.core.Session;
import io.tetrapod.core.rpc.Structure.Security;
import io.tetrapod.core.utils.*;
import io.tetrapod.core.utils.AuthToken.Decoded;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.identity.*;

import static io.tetrapod.protocol.core.TetrapodContract.*;

public class RequestContext {

   public final RequestHeader header;
   public final Session       session;

   public RequestContext(RequestHeader header, Session session) {
      this.header = header;
      this.session = session;
   }

   public Security getSenderSecurity() {
      if (header.fromType == Core.TYPE_TETRAPOD || header.fromType == Core.TYPE_SERVICE)
         return Security.INTERNAL;
      if (header.fromType == Core.TYPE_ADMIN)
         return Security.ADMIN;
      return Security.PUBLIC; 
   }

   public Security getSenderSecurity(int accountId, String authToken, Value<Integer> errorCode) {
      if (header.fromType == Core.TYPE_TETRAPOD || header.fromType == Core.TYPE_SERVICE)
         return Security.INTERNAL;
      Decoded d = AuthToken.decodeUserToken(authToken, accountId, header.fromId);
      if (d != null && d.timeLeft >= 0) {
         int perms = d.miscValues[0];
         int anyAdmin = User.PROPS_ADMIN_T1 | User.PROPS_ADMIN_T2 | User.PROPS_ADMIN_T3 | User.PROPS_ADMIN_T4; 
         if ((perms & anyAdmin) != 0) {
            // to get more detailed admin callers will have to decode auth token themselves
            return Security.ADMIN;
         }
         return Security.PROTECTED;
      } else {
         if (d.timeLeft < 0) {
            errorCode.set(ERROR_RIGHTS_EXPIRED);
         } else {
            errorCode.set(ERROR_UNKNOWN);
         }
         return Security.PUBLIC;
      }
   }
}
