package io.tetrapod.core.rpc;

import io.tetrapod.core.Session;
import io.tetrapod.core.rpc.Structure.Security;
import io.tetrapod.core.utils.*;
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
      int[] vals = { 0, 0, accountId, header.fromId };
      Value<Boolean> timedOut = new Value<>(false);
      if (AuthToken.decode(vals, 2, authToken, timedOut) && !timedOut.get()) {
         int perms = vals[0];
         int anyAdmin = User.PROPS_ADMIN_T1 | User.PROPS_ADMIN_T2 | User.PROPS_ADMIN_T3 | User.PROPS_ADMIN_T4; 
         if ((perms & anyAdmin) != 0) {
            // to get more detailed admin callers will have to decode auth token themselves
            return Security.ADMIN;
         }
         return Security.PROTECTED;
      } else {
         if (timedOut.get()) {
            errorCode.set(ERROR_RIGHTS_EXPIRED);
         } else {
            errorCode.set(ERROR_UNKNOWN);
         }
         return Security.PUBLIC;
      }
   }
}
