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

/**
 * If there is a conflict the user has three choices the client has to walk them through:<ul> <li> forget about linking [eg cancel] <li> force the link to this account [eg resend Link with overrideConflicts = true] <li> make the other account the main account, by:<ul> <li> Logout <li> Login with identity used in Link (gettng you conflict id) <li> Link with identity used to originally login with overrideConflicts = true </ul></ul> OR just tell them to contact support with the two account ids.
 */

@SuppressWarnings("unused")
public class LinkResponse extends Response {
   
   public static final int STRUCT_ID = 14285084;
   public static final int CONTRACT_ID = IdentityContract.CONTRACT_ID;
    
   public LinkResponse() {
      defaults();
   }

   public LinkResponse(boolean conflicts, int conflictId, String conflictName, int conflictNumLogins, long conflictLastLogin, long thisLastLogin, int thisNumLogins) {
      this.conflicts = conflicts;
      this.conflictId = conflictId;
      this.conflictName = conflictName;
      this.conflictNumLogins = conflictNumLogins;
      this.conflictLastLogin = conflictLastLogin;
      this.thisLastLogin = thisLastLogin;
      this.thisNumLogins = thisNumLogins;
   }   
   
   /**
    * true if there was a conflict, false means success
    */
   public boolean conflicts;
   
   /**
    * accountId of the conflicting account
    */
   public int conflictId;
   public String conflictName;
   public int conflictNumLogins;
   public long conflictLastLogin;
   public long thisLastLogin;
   public int thisNumLogins;

   public final Structure.Security getSecurity() {
      return Security.PROTECTED;
   }

   public final void defaults() {
      conflicts = false;
      conflictId = 0;
      conflictName = null;
      conflictNumLogins = 0;
      conflictLastLogin = 0;
      thisLastLogin = 0;
      thisNumLogins = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.conflicts);
      data.write(2, this.conflictId);
      data.write(3, this.conflictName);
      data.write(4, this.conflictNumLogins);
      data.write(5, this.conflictLastLogin);
      data.write(6, this.thisLastLogin);
      data.write(7, this.thisNumLogins);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.conflicts = data.read_boolean(tag); break;
            case 2: this.conflictId = data.read_int(tag); break;
            case 3: this.conflictName = data.read_string(tag); break;
            case 4: this.conflictNumLogins = data.read_int(tag); break;
            case 5: this.conflictLastLogin = data.read_long(tag); break;
            case 6: this.thisLastLogin = data.read_long(tag); break;
            case 7: this.thisNumLogins = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return LinkResponse.CONTRACT_ID;
   }

   public final int getStructId() {
      return LinkResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[7+1];
      result[1] = "conflicts";
      result[2] = "conflictId";
      result[3] = "conflictName";
      result[4] = "conflictNumLogins";
      result[5] = "conflictLastLogin";
      result[6] = "thisLastLogin";
      result[7] = "thisNumLogins";
      return result;
   }

   public final Structure make() {
      return new LinkResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[6] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[7] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }
 }
