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

@SuppressWarnings("all")
public class WebRootDef extends Structure {
   
   public static final int STRUCT_ID = 943242;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
    
   public WebRootDef() {
      defaults();
   }

   public WebRootDef(String name, String path, String file) {
      this.name = name;
      this.path = path;
      this.file = file;
   }   
   
   /**
    * semantic name/key of the web root
    */
   public String name;
   
   /**
    * resource root path name
    */
   public String path;
   
   /**
    * location of static files to serve
    */
   public String file;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      name = null;
      path = null;
      file = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.name);
      data.write(2, this.path);
      data.write(3, this.file);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.name = data.read_string(tag); break;
            case 2: this.path = data.read_string(tag); break;
            case 3: this.file = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   public final int getContractId() {
      return WebRootDef.CONTRACT_ID;
   }

   public final int getStructId() {
      return WebRootDef.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "name";
      result[2] = "path";
      result[3] = "file";
      return result;
   }

   public final Structure make() {
      return new WebRootDef();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "WebRootDef";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

   @Override
   @SuppressWarnings("RedundantIfStatement")
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      WebRootDef that = (WebRootDef) o;

      if (name != null ? !name.equals(that.name) : that.name != null)
         return false;
      if (path != null ? !path.equals(that.path) : that.path != null)
         return false;
      if (file != null ? !file.equals(that.file) : that.file != null)
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = 0;
      result = 31 * result + (name != null ? name.hashCode() : 0);
      result = 31 * result + (path != null ? path.hashCode() : 0);
      result = 31 * result + (file != null ? file.hashCode() : 0);
      return result;
   }

}
