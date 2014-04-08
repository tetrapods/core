package io.tetrapod.protocol.identity;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.*;
import io.tetrapod.protocol.core.*;

import java.io.IOException;

@SuppressWarnings("unused")
public class Identity extends Structure {
   
   public static final int IDENTITY_EMAIL = 1; 
   public static final int IDENTITY_DEVICE = 2; 
   public static final int IDENTITY_FACEBOOK = 3; 
   public static final int IDENTITY_TWITTER = 4; 
   public static final int IDENTITY_OAUTH = 5; 
   
   public static final int STRUCT_ID = 12701893;
   public static final int CONTRACT_ID = IdentityContract.CONTRACT_ID;
    
   public Identity() {
      defaults();
   }

   public Identity(int type, String publicPart, String verifyPart) {
      this.type = type;
      this.publicPart = publicPart;
      this.verifyPart = verifyPart;
   }   
   
   public int type;
   
   /**
    * eg: your email address
    */
   public String publicPart;
   
   /**
    * eg: your password (or password hash if stored internally)
    */
   public String verifyPart;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      type = 0;
      publicPart = null;
      verifyPart = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.type);
      data.write(2, this.publicPart);
      data.write(3, this.verifyPart);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.type = data.read_int(tag); break;
            case 2: this.publicPart = data.read_string(tag); break;
            case 3: this.verifyPart = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return Identity.CONTRACT_ID;
   }

   public final int getStructId() {
      return Identity.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "type";
      result[2] = "publicPart";
      result[3] = "verifyPart";
      return result;
   }

   public final Structure make() {
      return new Identity();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }
}
