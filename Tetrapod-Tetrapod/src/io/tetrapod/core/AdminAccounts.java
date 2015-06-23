package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.TYPE_ADMIN;
import io.tetrapod.core.rpc.RequestContext;
import io.tetrapod.core.storage.*;
import io.tetrapod.core.utils.*;
import io.tetrapod.protocol.core.Admin;
import io.tetrapod.raft.*;
import io.tetrapod.raft.RaftRPC.ClientResponseHandler;

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

   public Admin getAdminByAccountId(int accountId) {
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

   // TODO: Lock + Put can be replaced with a conditional put once supported
   public Admin mutate(Admin presumedCurrent, AdminMutator mutator) {
      try {
         final String key = "admin::" + presumedCurrent.accountId;
         final DistributedLock lock = cluster.getLock(key);
         lock.lock(60000, 60000);
         try {
            Admin admin = getAdminByAccountId(presumedCurrent.accountId);
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
            Admin admin = getAdminByAccountId(d.accountId);
            if (admin != null) {
               return true;
            }
         }
      }
      return false;
   }

}
