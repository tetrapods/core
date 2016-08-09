package io.tetrapod.core.rpc;

import static io.tetrapod.protocol.core.CoreContract.*;

import io.tetrapod.core.Session;
import io.tetrapod.core.rpc.Structure.Security;
import io.tetrapod.core.utils.*;
import io.tetrapod.core.utils.LoginAuthToken.DecodedSession;
import io.tetrapod.protocol.core.*;

public class SessionRequestContext extends RequestContext {

   private final static boolean USE_SECURITY = true;

   public final Session         session;

   public SessionRequestContext(RequestHeader header, Session session) {
      super(header);
      this.session = session;
   }

   @Override
   public void handlePendingResponse(Response res, int originalRequestId) {
      assert res != Response.PENDING;
      session.sendResponse(res, originalRequestId);
   }

   @Override
   public Response securityCheck(Request request) {
      if (USE_SECURITY) {
         Security mine = request.getSecurity();
         Security theirs = getSenderSecurity();
         if (theirs.ordinal() < mine.ordinal())
            return Response.error(ERROR_INVALID_RIGHTS);
      }
      return null;
   }

   @Override
   public Response securityCheck(Request request, int accountId, String authToken, int adminRightsRequired) {
      if (USE_SECURITY) {
         Value<Integer> error = new Value<>(ERROR_INVALID_RIGHTS);
         Security mine = request.getSecurity();
         Security theirs = getSenderSecurity(accountId, authToken, error);
         if (header.fromType == Core.TYPE_SERVICE || header.fromType == Core.TYPE_TETRAPOD) {
            theirs = Security.INTERNAL;
         } else if (mine == Security.ADMIN) {
            AdminAuthToken.validateAdminToken(accountId, authToken, adminRightsRequired);
            theirs = Security.ADMIN;
         }
         if (theirs.ordinal() < mine.ordinal()) {
            return new Error(error.get());
         }
      }
      return null;
   }

   private Security getSenderSecurity() {
      if (header.fromType == Core.TYPE_TETRAPOD || header.fromType == Core.TYPE_SERVICE)
         return Security.INTERNAL;
      return Security.PUBLIC;
   }

   private Security getSenderSecurity(int accountId, String authToken, Value<Integer> errorCode) {
      Security senderSecurity = getSenderSecurity();
      if (senderSecurity == Security.PUBLIC) {
         // upgrade them to protected if their token is good
         DecodedSession d = LoginAuthToken.decodeSessionToken(authToken, accountId, header.fromParentId);
         if (d != null) {
            if (d.timeLeft >= 0 && d.accountId == accountId) {
               senderSecurity = Security.PROTECTED;
            }
         } else { // see if the token is an admin token
            try {
               AdminAuthToken.validateAdminToken(accountId, authToken, 0);
               if (header.fromType == Core.TYPE_CLIENT) {
                  header.fromType = Core.TYPE_ADMIN;
               }
               return Security.ADMIN;
            } catch (Exception e) {}
         }
         // handle errors
         if (d == null) {
            errorCode.set(ERROR_SECURITY);
         } else {
            if (d.accountId != accountId) {
               errorCode.set(ERROR_INVALID_RIGHTS);
            } else if (d.timeLeft < 0) {
               errorCode.set(ERROR_RIGHTS_EXPIRED);
            }
         }
      }
      return senderSecurity;
   }

}
