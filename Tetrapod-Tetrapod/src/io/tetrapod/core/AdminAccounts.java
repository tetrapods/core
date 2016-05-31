package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.TYPE_ADMIN;
import static io.tetrapod.protocol.core.CoreContract.ERROR_INVALID_RIGHTS;
import static io.tetrapod.protocol.core.CoreContract.ERROR_UNKNOWN;
import static io.tetrapod.protocol.core.TetrapodContract.ERROR_INVALID_ACCOUNT;
import static io.tetrapod.protocol.core.TetrapodContract.ERROR_INVALID_CREDENTIALS;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.storage.*;
import io.tetrapod.core.utils.*;
import io.tetrapod.protocol.core.*;

/**
 * Manages tetrapod administration accounts
 */
public class AdminAccounts {

   public static final Logger logger = LoggerFactory.getLogger(AdminAccounts.class);
   public static final Logger auditLogger = LoggerFactory.getLogger("audit");

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
      cluster.executeCommand(new AddAdminUserCommand(admin), e -> {
         if (e != null) {
            AddAdminUserCommand cmd = (AddAdminUserCommand) e.getCommand();
            val.set(cmd.getAdminUser());
         } else {
            val.set(null);
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
         if (lock.lock(45000, 60000)) {
            try {
               Admin admin = getAdmin(presumedCurrent.accountId);
               mutator.mutate(admin);
               cluster.modify(admin);
               logger.debug("Mutated {}", key);
               return admin;
            } finally {
               lock.unlock();
            }
         }
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
      }
      return null;
   }

   /**
    * Records a login attempt and returns true if we trip the flood alarm
    */
   public boolean recordLoginAttempt(Admin admin) {
      admin = mutate(admin, admin1 -> {
         if (admin1.loginAttempts == null) {
            admin1.loginAttempts = new long[Admin.MAX_LOGIN_ATTEMPTS];
         }
         for (int j = admin1.loginAttempts.length - 1; j > 0; j--) {
            admin1.loginAttempts[j] = admin1.loginAttempts[j - 1];
         }
         admin1.loginAttempts[0] = System.currentTimeMillis();
      });
      if (admin != null) {
         return ((System.currentTimeMillis() - admin.loginAttempts[admin.loginAttempts.length - 1]) < 5000);
      }
      return true;
   }

   public boolean verifyPermission(Admin admin, long systemRightsRequired) {
      return (admin.rights & systemRightsRequired) == systemRightsRequired;
   }

   public boolean isValidAdminRequest(RequestContext ctx, String adminToken) {
      return isValidAdminRequest(ctx, adminToken, 0);
   }

   public boolean isValidAdminRequest(RequestContext ctx, String adminToken, long rightsRequired) {
      if (ctx.header.fromType == TYPE_ADMIN) {
         final AdminAuthToken.Decoded d = AdminAuthToken.decodeSessionToken(adminToken);
         if (d != null) {
            final Admin admin = getAdmin(d.accountId);
            if (admin != null) {
               if (verifyPermission(admin, rightsRequired)) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   public Admin getAdmin(RequestContext ctx, String adminToken, long rightsRequired) {
      if (ctx.header.fromType == TYPE_ADMIN) {
         final AdminAuthToken.Decoded d = AdminAuthToken.decodeSessionToken(adminToken);
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
      final int accountId = AdminAuthToken.decodeLoginToken(r.token);
      if (accountId != 0) {
         final Admin admin = getAdmin(accountId);
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
               logger.info("Invalid Credentials - brute force protection ({})", admin.email);
               auditLogger.info("Invalid Credentials - brute force protection ({})", admin.email);
               return new Error(ERROR_INVALID_CREDENTIALS); // prevent brute force attack
            }
            if (PasswordHash.validatePassword(r.password, admin.hash)) {
               // mark the session as an admin
               ctx.session.theirType = Core.TYPE_ADMIN;
               auditLogger.info("Admin {} [{}] has logged in", r.email, admin.accountId);
               final String authtoken = AdminAuthToken.encodeLoginToken(admin.accountId, 60 * 24 * 14);
               return new AdminLoginResponse(authtoken, admin.accountId);
            } else {
               auditLogger.info("Admin {} [{}] attempted to log in, invalid credentials", r.email, admin.accountId);
               return new Error(ERROR_INVALID_CREDENTIALS); // invalid password
            }
         } else {
            auditLogger.info("Admin {} attempted to log in, invalid account", r.email);
            return new Error(ERROR_INVALID_CREDENTIALS); // invalid account
         }
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
         return new Error(ERROR_UNKNOWN);
      }
   }

   public Response requestAdminChangePassword(final AdminChangePasswordRequest r, RequestContext ctx) {
      Admin admin = getAdmin(ctx, r.token, 0);
      try {
         if (PasswordHash.validatePassword(r.oldPassword, admin.hash)) {
            final String newHash = PasswordHash.createHash(r.newPassword);
            admin = mutate(admin, a -> a.hash = newHash);
            if (admin != null) {
               auditLogger.info("Admin {} [{}] updated their password", admin.email, admin.accountId);
               return Response.SUCCESS;
            }
         } else {
            auditLogger.info("Admin {} [{}] derped their attempt to update their password", admin.email, admin.accountId);
            return new Error(ERROR_INVALID_CREDENTIALS);
         }
      } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
         logger.error(e.getMessage(), e);
      }
      return new Error(ERROR_UNKNOWN);
   }

   public Response requestAdminResetPassword(AdminResetPasswordRequest r, RequestContext ctx) {
      getAdmin(ctx, r.token, Admin.RIGHTS_USER_WRITE);
      try {
         Admin target = getAdmin(r.accountId);
         if (target != null) {
            final String newHash = PasswordHash.createHash(r.password);
            target = mutate(target, a -> a.hash = newHash);
            if (target != null) {
               auditLogger.info("Admin [{}] reset the password for user {} [{}]", r.accountId, target.email, target.accountId);
               return Response.SUCCESS;
            }
         }
      } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
         logger.error(e.getMessage(), e);
      }
      return new Error(ERROR_UNKNOWN);
   }

   public Response requestAdminCreate(AdminCreateRequest r, RequestContext ctx) {
      final Admin admin = getAdmin(ctx, r.token, Admin.RIGHTS_USER_WRITE);
      assert(admin != null);
      try {
         final String hash = PasswordHash.createHash(r.password);
         final Admin newUser = addAdmin(r.email.trim(), hash, r.rights);
         if (newUser != null) {
            auditLogger.info("Admin {} [{}] created an admin account for user {} [{}]",
                  admin.email, admin.accountId, newUser.email, newUser.accountId);
            return Response.SUCCESS;
         } else {
            // they probably already exist
            auditLogger.info("Admin {} [{}] failed to create a new admin account.  Account may already exist.",
                  admin.email, admin.accountId);
            return new Error(ERROR_INVALID_ACCOUNT);
         }
      } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
         logger.error(e.getMessage(), e);
         return new Error(ERROR_UNKNOWN);
      }
   }

   public Response requestAdminDelete(AdminDeleteRequest r, RequestContext ctx) {
      final Admin admin = getAdmin(ctx, r.token, Admin.RIGHTS_USER_WRITE);
      // don't allow deleting ourselves or the default admin user
      if (r.accountId == 1 && admin.accountId == r.accountId) {
         return new Error(ERROR_INVALID_RIGHTS);
      }
      final Admin target = getAdmin(r.accountId);
      if (target != null) {
         final Value<Boolean> val = new Value<Boolean>();
         cluster.executeCommand(new DelAdminUserCommand(r.accountId), e -> val.set(e != null));
         if (val.waitForValue()) {
            auditLogger.info("Admin {} [{}] deleted the admin account for user {} [{}]",
                  admin.email, admin.accountId, target.email, target.accountId);
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
      // don't allow changing our user rights to prevent accidental lockout
      if (admin.accountId == r.accountId) {
         final int preserve = Admin.RIGHTS_USER_READ | Admin.RIGHTS_USER_WRITE;
         if ((r.rights & preserve) != preserve) {
            return new Error(ERROR_INVALID_RIGHTS);
         }
      }
      final Admin target = getAdmin(r.accountId);
      if (target != null) {
         Admin mutated = mutate(target, a -> a.rights = r.rights);
         if (mutated != null) {
            auditLogger.info("Admin {} [{}] updated permissions for user {} [{}].  New rights:{}",
                  admin.email, admin.accountId, target.email, target.accountId, r.rights);
            return Response.SUCCESS;
         }
         return new Error(ERROR_UNKNOWN);
      } else {
         return new Error(ERROR_INVALID_ACCOUNT);
      }
   }

   public Response requestAdminSessionToken(AdminSessionTokenRequest r, RequestContext ctx) {
      try {
         final int accountId = AdminAuthToken.decodeLoginToken(r.authToken);
         if (accountId != 0) {
            final Admin admin = getAdmin(accountId);
            assert(admin != null);
            if (admin != null) {
               return new AdminSessionTokenResponse(AdminAuthToken.encodeSessionToken(admin.accountId, 15, admin.rights));
            } else {
               return new Error(ERROR_INVALID_ACCOUNT);
            }
         } else {
            return new Error(ERROR_INVALID_RIGHTS);
         }
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
         return new Error(ERROR_UNKNOWN);
      }
   }
}
