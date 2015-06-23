package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.TYPE_ADMIN;
import static io.tetrapod.protocol.core.CoreContract.*;
import static io.tetrapod.protocol.core.TetrapodContract.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.storage.*;
import io.tetrapod.core.utils.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.raft.*;
import io.tetrapod.raft.RaftRPC.ClientResponseHandler;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import org.slf4j.*;

/**
 * Manages tetrapod administration accounts
 */
public class AdminAccounts {

   public static final Logger    logger = LoggerFactory.getLogger(AdminAccounts.class);

   private final TetrapodCluster cluster;

   public AdminAccounts(TetrapodCluster storage) {
      this.cluster = storage;

      // will add the default admin user if missing
      try {
         addAdmin("admin@localhost", PasswordHash.createHash("admin"), 0xFF);
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
      }
   }

   public Admin getAdminByEmail(String email) {
      for (Admin admin : cluster.getAdmins()) {
         if (admin.email.equalsIgnoreCase(email)) {
            return admin;
         }
      }
      return null;
   }

   public Admin getAdmin(int accountId) {
      return cluster.getAdmin(accountId);
   }

   public Admin addAdmin(String email, String hash, long rights) {
      final Value<Admin> val = new Value<Admin>();
      final Admin admin = new Admin(0, email, hash, rights, new long[Admin.MAX_LOGIN_ATTEMPTS]);
      cluster.executeCommand(new AddAdminUserCommand(admin), new ClientResponseHandler<TetrapodStateMachine>() {
         @Override
         public void handleResponse(Entry<TetrapodStateMachine> e) {
            if (e != null) {
               AddAdminUserCommand cmd = (AddAdminUserCommand) e.getCommand();
               val.set(cmd.getAdminUser());
            } else {
               val.set(null);
            }
         }
      });
      return val.waitForValue();
   }

   public interface AdminMutator {
      void mutate(Admin admin);
   }

   // TODO: Lock can be replaced with a conditional put once supported
   public Admin mutate(Admin presumedCurrent, AdminMutator mutator) {
      try {
         final String key = "admin::" + presumedCurrent.accountId;
         final DistributedLock lock = cluster.getLock(key);
         lock.lock(60000, 60000);
         try {
            Admin admin = getAdmin(presumedCurrent.accountId);
            mutator.mutate(admin);
            cluster.modify(admin);
            logger.debug("Mutated {}", key);
            return admin;
         } finally {
            lock.unlock();
         }
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
         return null;
      }
   }

   /**
    * Records a login attempt and returns true if we trip the flood alarm
    */
   public boolean recordLoginAttempt(Admin admin) {
      admin = mutate(admin, new AdminMutator() {
         @Override
         public void mutate(Admin admin) {
            if (admin.loginAttempts == null) {
               admin.loginAttempts = new long[Admin.MAX_LOGIN_ATTEMPTS];
            }
            for (int j = admin.loginAttempts.length - 1; j > 0; j--) {
               admin.loginAttempts[j] = admin.loginAttempts[j - 1];
            }
            admin.loginAttempts[0] = System.currentTimeMillis();
         }
      });
      if (admin != null) {
         return ((System.currentTimeMillis() - admin.loginAttempts[admin.loginAttempts.length - 1]) < 5000);
      }
      return true;
   }

   public boolean verifyPermission(Admin admin, int rightsRequired) {
      return (admin.rights & rightsRequired) == rightsRequired;
   }

   public boolean isValidAdminRequest(RequestContext ctx, String adminToken) {
      if (ctx.header.fromType == TYPE_ADMIN) {
         AuthToken.Decoded d = AuthToken.decodeAuthToken1(adminToken);
         if (d != null) {
            Admin admin = getAdmin(d.accountId);
            if (admin != null) {
               return true;
            }
         }
      }
      return false;
   }

   public Admin getAdmin(RequestContext ctx, String adminToken, int rightsRequired) {
      if (ctx.header.fromType == TYPE_ADMIN) {
         final AuthToken.Decoded d = AuthToken.decodeAuthToken1(adminToken);
         if (d != null) {
            final Admin admin = getAdmin(d.accountId);
            if (admin != null) {
               if (verifyPermission(admin, rightsRequired)) {
                  return admin;
               }
            }
         }
      }
      throw new ErrorResponseException(ERROR_INVALID_RIGHTS);
   }

   ///////////////////////// RPC HANDLERS /////////////////////// 

   public Response requestAdminAuthorize(AdminAuthorizeRequest r, RequestContext ctxA) {
      SessionRequestContext ctx = (SessionRequestContext) ctxA;
      AuthToken.Decoded d = AuthToken.decodeAuthToken1(r.token);
      if (d != null) {
         logger.debug("TOKEN {} time left = {}", r.token, d.timeLeft);
         Admin admin = getAdmin(d.accountId);
         if (admin != null) {
            ctx.session.theirType = Core.TYPE_ADMIN;
            return new AdminAuthorizeResponse(admin.accountId, admin.email);
         }
      } else {
         logger.warn("TOKEN {} NOT VALID", r.token);
      }
      return new Error(ERROR_INVALID_RIGHTS);
   }

   public Response requestAdminLogin(AdminLoginRequest r, RequestContext ctxA) {
      SessionRequestContext ctx = (SessionRequestContext) ctxA;
      if (r.email == null) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      try {
         Admin admin = getAdminByEmail(r.email);
         if (admin != null) {
            if (recordLoginAttempt(admin)) {
               logger.info("Invalid Credentials");
               return new Error(ERROR_INVALID_CREDENTIALS); // prevent brute force attack
            }
            if (PasswordHash.validatePassword(r.password, admin.hash)) {
               // mark the session as an admin
               ctx.session.theirType = Core.TYPE_ADMIN;
               final String authtoken = AuthToken.encodeAuthToken1(admin.accountId, 0, 60 * 24 * 14);
               return new AdminLoginResponse(authtoken, admin.accountId);
            } else {
               return new Error(ERROR_INVALID_CREDENTIALS); // invalid password
            }
         } else {
            return new Error(ERROR_INVALID_CREDENTIALS); // invalid account
         }
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
         return new Error(ERROR_UNKNOWN);
      }
   }

   public Response requestAdminChangePassword(final AdminChangePasswordRequest r, RequestContext ctxA) {
      if (ctxA.header.fromType != TYPE_ADMIN) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      AuthToken.Decoded d = AuthToken.decodeAuthToken1(r.token);
      if (d != null) {
         Admin admin = getAdmin(d.accountId);
         if (admin != null) {
            try {
               if (PasswordHash.validatePassword(r.oldPassword, admin.hash)) {
                  final String newHash = PasswordHash.createHash(r.newPassword);
                  admin = mutate(admin, new AdminMutator() {
                     @Override
                     public void mutate(Admin admin) {
                        admin.hash = newHash;
                     }
                  });
                  if (admin != null) {
                     return Response.SUCCESS;
                  }
               } else {
                  return new Error(ERROR_INVALID_CREDENTIALS);
               }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
               logger.error(e.getMessage(), e);
            }
         }
      } else {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      return new Error(ERROR_UNKNOWN);
   }

   public Response requestAdminResetPassword(AdminResetPasswordRequest r, RequestContext ctx) {
      if (ctx.header.fromType != TYPE_ADMIN) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      AuthToken.Decoded d = AuthToken.decodeAuthToken1(r.token);
      if (d != null) {
         final Admin admin = getAdmin(d.accountId);
         if (admin != null && verifyPermission(admin, Admin.RIGHTS_USER_WRITE)) {
            try {
               Admin target = getAdmin(r.accountId);
               if (target != null) {
                  final String newHash = PasswordHash.createHash(r.password);
                  target = mutate(target, new AdminMutator() {
                     @Override
                     public void mutate(Admin a) {
                        a.hash = newHash;
                     }
                  });
                  if (target != null) {
                     return Response.SUCCESS;
                  }
               }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
               logger.error(e.getMessage(), e);
            }
         } else {
            return new Error(ERROR_INVALID_RIGHTS);
         }
      } else {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      return new Error(ERROR_UNKNOWN);
   }

   public Response requestAdminCreate(AdminCreateRequest r, RequestContext ctx) {
      if (ctx.header.fromType != TYPE_ADMIN) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      final AuthToken.Decoded d = AuthToken.decodeAuthToken1(r.token);
      if (d != null) {
         final Admin admin = getAdmin(d.accountId);
         if (admin != null) {
            try {
               if (verifyPermission(admin, Admin.RIGHTS_USER_WRITE)) {
                  final String hash = PasswordHash.createHash(r.password);

                  final Admin newUser = addAdmin(r.email.trim(), hash, r.rights);
                  if (newUser != null) {
                     return Response.SUCCESS;
                  } else {
                     // they probably already exist
                     return new Error(ERROR_INVALID_ACCOUNT);
                  }
               } else {
                  return new Error(ERROR_INVALID_RIGHTS);
               }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
               logger.error(e.getMessage(), e);
            }
         }
      } else {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      return new Error(ERROR_UNKNOWN);
   }

   public Response requestAdminDelete(AdminDeleteRequest r, RequestContext ctx) {
      final Admin admin = getAdmin(ctx, r.token, Admin.RIGHTS_USER_WRITE);
      if (admin.accountId == r.accountId) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      final Admin target = getAdmin(r.accountId);
      if (target != null) {
         final Value<Boolean> val = new Value<Boolean>();
         cluster.executeCommand(new DelAdminUserCommand(r.accountId), new ClientResponseHandler<TetrapodStateMachine>() {
            @Override
            public void handleResponse(Entry<TetrapodStateMachine> e) {
               val.set(e != null);
            }
         });
         if (val.waitForValue()) {
            return Response.SUCCESS;
         } else {
            return new Error(ERROR_UNKNOWN);
         }
      } else {
         return new Error(ERROR_INVALID_ACCOUNT);
      }
   }

   public Response requestAdminChangeRights(final AdminChangeRightsRequest r, RequestContext ctx) {
      final Admin admin = getAdmin(ctx, r.token, Admin.RIGHTS_USER_WRITE);
      if (admin.accountId == r.accountId) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      final Admin target = getAdmin(r.accountId);
      if (target != null) {
         Admin mutated = mutate(target, new AdminMutator() {
            @Override
            public void mutate(Admin a) {
               a.rights = r.rights;
            }
         });
         if (mutated != null) {
            return Response.SUCCESS;
         }
         return new Error(ERROR_UNKNOWN);
      } else {
         return new Error(ERROR_INVALID_ACCOUNT);
      }
   }
}
