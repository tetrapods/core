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
      int perms = Auth.decode(accountId, header.fromId, authToken);
      if (perms < 0) {
         switch (perms) {
            case -1: 
               errorCode.set(ERROR_INVALID_RIGHTS); 
               break;
            case -2: 
               errorCode.set(ERROR_RIGHTS_EXPIRED); 
               break;
            case -3: 
               errorCode.set(ERROR_UNKNOWN); 
               break;
            case -4:
               // auth not set up
               errorCode.set(ERROR_UNKNOWN); 
               break;
         }
         return Security.PUBLIC;
      }
      int anyAdmin = User.PROPS_ADMIN_T1 | User.PROPS_ADMIN_T2 | User.PROPS_ADMIN_T3 | User.PROPS_ADMIN_T4; 
      if ((perms & anyAdmin) != 0) {
         // to get more detailed admin callers will have to decode auth token themselves
         return Security.ADMIN;
      }
      return Security.PROTECTED;
   }
}
