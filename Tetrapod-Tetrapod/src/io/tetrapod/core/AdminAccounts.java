package io.tetrapod.core;

import io.tetrapod.core.storage.Storage;
import io.tetrapod.core.utils.*;
import io.tetrapod.protocol.core.Admin;

import org.slf4j.*;

import com.hazelcast.core.HazelcastInstanceNotActiveException;

/**
 * Manages tetrapod administration accounts
 */
public class AdminAccounts {

   public static final Logger logger = LoggerFactory.getLogger(AdminAccounts.class);
   private final Storage      storage;

   public AdminAccounts(Storage storage) {
      this.storage = storage;
   }

   public Admin getAdminByEmail(String email) {
      Admin admin = null;
      try {
         int accountId = storage.get("admin.email::" + email, 0);
         admin = getAdminByAccountId(accountId);
         if (admin == null) {
            // check for default admin user
            final String defaultAdminEmail = "admin@" + Util.getProperty("product.url", "tetrapod.io");
            if (email.equals(defaultAdminEmail)) {
               String defaultPassword = Util.getProperty("admin.default.password", "admin");
               admin = addAdmin(defaultAdminEmail, PasswordHash.createHash(defaultPassword), 0xFF);
            } else {
               admin = null;
            }
         }
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
      }
      return admin;
   }

   public Admin getAdminByAccountId(int accountId) {
      if (accountId > 0) {
         try {
            return storage.read("admin::" + accountId, new Admin());
         } catch (Exception e) {
            logger.error(e.getMessage(), e);
         }
      }
      return null;
   }

   public Admin addAdmin(String email, String hash, long rights) {
      try {
         int accountId = (int) storage.increment("admin.accounts");
         Admin admin = new Admin(accountId, email, hash, 0xFF, new long[Admin.MAX_LOGIN_ATTEMPTS]);
         storage.put("admin::" + accountId, admin);
         storage.put("admin.email::" + email, accountId);
         return admin;
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
      }
      return null;
   }

   public interface AdminMutator {
      void mutate(Admin admin);
   }

   public Admin mutate(Admin presumedCurrent, AdminMutator mutator) {
      try {
         final String key = "admin::" + presumedCurrent.accountId;
         storage.getLock(key).lock();
         try {
            Admin admin = getAdminByAccountId(presumedCurrent.accountId);
            mutator.mutate(admin);
            storage.put(key, admin);
            logger.debug("Mutated {}", key);
            return admin;
         } finally {
            storage.getLock(key).unlock();
         }
      } catch (HazelcastInstanceNotActiveException e) {
         throw Fail.fail(e);
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

}
