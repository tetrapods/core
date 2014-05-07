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
   
   public static final int RIGHTS_CLUSTER_READ = 1; 
   public static final int RIGHTS_CLUSTER_WRITE = 2; 
   public static final int RIGHTS_USER_READ = 4; 
   public static final int RIGHTS_USER_WRITE = 8; 
   
   public static final int STRUCT_ID = 16753598;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
    
   public Admin() {
      defaults();
   }

   public Admin(int accountId, String email, String hash, long rights) {
      this.accountId = accountId;
      this.email = email;
      this.hash = hash;
      this.rights = rights;
   }   
   
   public int accountId;
   public String email;
   public String hash;
   public long rights;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      accountId = 0;
      email = null;
      hash = null;
      rights = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.accountId);
      data.write(2, this.email);
      data.write(3, this.hash);
      data.write(4, this.rights);
      data.writeEndTag();
   }
   
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

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[4+1];
      result[1] = "accountId";
      result[2] = "email";
      result[3] = "hash";
      result[4] = "rights";
      return result;
   }

   public final Structure make() {
      return new Admin();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      return desc;
   }
}
