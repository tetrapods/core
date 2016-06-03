package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import io.tetrapod.protocol.core.TypeDescriptor;
import io.tetrapod.protocol.core.StructDescription;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class Admin extends Structure {
   
   public static final int MAX_LOGIN_ATTEMPTS = 5; 
   public static final int RIGHTS_CLUSTER_READ = 1; 
   public static final int RIGHTS_CLUSTER_WRITE = 2; 
   public static final int RIGHTS_USER_READ = 4; 
   public static final int RIGHTS_USER_WRITE = 8; 
   public static final int RIGHTS_RESERVED_1 = 16; 
   public static final int RIGHTS_RESERVED_2 = 32; 
   public static final int RIGHTS_RESERVED_3 = 64; 
   public static final int RIGHTS_RESERVED_4 = 128; 
   public static final int RIGHTS_APP_SET_1 = 256; 
   public static final int RIGHTS_APP_SET_2 = 512; 
   public static final int RIGHTS_APP_SET_3 = 1024; 
   public static final int RIGHTS_APP_SET_4 = 2048; 
   
   public static final int STRUCT_ID = 16753598;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
    
   public Admin() {
      defaults();
   }

   public Admin(int accountId, String email, String hash, long rights, long[] loginAttempts) {
      this.accountId = accountId;
      this.email = email;
      this.hash = hash;
      this.rights = rights;
      this.loginAttempts = loginAttempts;
   }   
   
   public int accountId;
   public String email;
   public String hash;
   public long rights;
   public long[] loginAttempts;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      accountId = 0;
      email = null;
      hash = null;
      rights = 0;
      loginAttempts = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.accountId);
      data.write(2, this.email);
      data.write(3, this.hash);
      data.write(4, this.rights);
      if (this.loginAttempts != null) data.write(5, this.loginAttempts);
      data.writeEndTag();
   }
   
   @SuppressWarnings("Duplicates")
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.accountId = data.read_int(tag); break;
            case 2: this.email = data.read_string(tag); break;
            case 3: this.hash = data.read_string(tag); break;
            case 4: this.rights = data.read_long(tag); break;
            case 5: this.loginAttempts = data.read_long_array(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   public final int getContractId() {
      return Admin.CONTRACT_ID;
   }

   public final int getStructId() {
      return Admin.STRUCT_ID;
   }

   @SuppressWarnings("Duplicates")
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[5+1];
      result[1] = "accountId";
      result[2] = "email";
      result[3] = "hash";
      result[4] = "rights";
      result[5] = "loginAttempts";
      return result;
   }

   public final Structure make() {
      return new Admin();
   }

   protected boolean isSensitive(String fieldName) {
      if (fieldName.equals("email")) return true;
      if (fieldName.equals("hash")) return true;
      return false;
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "Admin";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_LONG_LIST, 0, 0);
      return desc;
   }

   @Override
   @SuppressWarnings("RedundantIfStatement")
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      Admin that = (Admin) o;

      if (accountId != that.accountId)
         return false;
      if (email != null ? !email.equals(that.email) : that.email != null)
         return false;
      if (hash != null ? !hash.equals(that.hash) : that.hash != null)
         return false;
      if (rights != that.rights)
         return false;
      if (!Arrays.equals(loginAttempts, that.loginAttempts))
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = 0;
      result = 31 * result + accountId;
      result = 31 * result + (email != null ? email.hashCode() : 0);
      result = 31 * result + (hash != null ? hash.hashCode() : 0);
      result = 31 * result + (int) (rights ^ (rights >>> 32));
      result = 31 * result + Arrays.hashCode(loginAttempts);
      return result;
   }

}
