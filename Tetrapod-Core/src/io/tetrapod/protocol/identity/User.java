package io.tetrapod.protocol.identity;

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
public class User extends Structure {
   
   public static final int PROPS_DEVELOPER = 1; 
   public static final int PROPS_ADMIN_T1 = 2; 
   public static final int PROPS_ADMIN_T2 = 4; 
   public static final int PROPS_ADMIN_T3 = 8; 
   public static final int PROPS_ADMIN_T4 = 16; 
   public static final int PROPS_BANNED_T1 = 32; 
   public static final int PROPS_BANNED_T2 = 64; 
   public static final int PROPS_BANNED_T3 = 128; 
   
   /**
    * user has not set a password
    */
   public static final int PROPS_NO_PASSWORD = 256; 
   
   public static final int STRUCT_ID = 10894876;
   public static final int CONTRACT_ID = IdentityContract.CONTRACT_ID;
    
   public User() {
      defaults();
   }

   public User(String username, String email, int accountId, int properties, int numLogins, long[] loginTimes, Identity[] identities) {
      this.username = username;
      this.email = email;
      this.accountId = accountId;
      this.properties = properties;
      this.numLogins = numLogins;
      this.loginTimes = loginTimes;
      this.identities = identities;
   }   
   
   public String username;
   public String email;
   public int accountId;
   public int properties;
   public int numLogins;
   public long[] loginTimes;
   public Identity[] identities;

   public final Structure.Security getSecurity() {
      return Security.PRIVATE;
   }

   public final void defaults() {
      username = null;
      email = null;
      accountId = 0;
      properties = 0;
      numLogins = 0;
      loginTimes = null;
      identities = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.username);
      data.write(2, this.email);
      data.write(3, this.accountId);
      data.write(4, this.properties);
      data.write(5, this.numLogins);
      if (this.loginTimes != null) data.write(6, this.loginTimes);
      if (this.identities != null) data.write(7, this.identities);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.username = data.read_string(tag); break;
            case 2: this.email = data.read_string(tag); break;
            case 3: this.accountId = data.read_int(tag); break;
            case 4: this.properties = data.read_int(tag); break;
            case 5: this.numLogins = data.read_int(tag); break;
            case 6: this.loginTimes = data.read_long_array(tag); break;
            case 7: this.identities = data.read_struct_array(tag, new Identity()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return User.CONTRACT_ID;
   }

   public final int getStructId() {
      return User.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[7+1];
      result[1] = "username";
      result[2] = "email";
      result[3] = "accountId";
      result[4] = "properties";
      result[5] = "numLogins";
      result[6] = "loginTimes";
      result[7] = "identities";
      return result;
   }

   public final Structure make() {
      return new User();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[6] = new TypeDescriptor(TypeDescriptor.T_LONG_LIST, 0, 0);
      desc.types[7] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, Identity.CONTRACT_ID, Identity.STRUCT_ID);
      return desc;
   }
}
