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
public class ServerAddress extends Structure {
   
   public static final int STRUCT_ID = 14893956;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
    
   public ServerAddress() {
      defaults();
   }

   public ServerAddress(String host, int port) {
      this.host = host;
      this.port = port;
   }   
   
   public String host;
   public int port;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      host = null;
      port = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.host);
      data.write(2, this.port);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.host = data.read_string(tag); break;
            case 2: this.port = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   public final int getContractId() {
      return ServerAddress.CONTRACT_ID;
   }

   public final int getStructId() {
      return ServerAddress.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "host";
      result[2] = "port";
      return result;
   }

   public final Structure make() {
      return new ServerAddress();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "ServerAddress";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }

   @Override
   @SuppressWarnings("RedundantIfStatement")
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      ServerAddress that = (ServerAddress) o;

      if (host != null ? !host.equals(that.host) : that.host != null)
         return false;
      if (port != that.port)
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = 0;
      result = 31 * result + (host != null ? host.hashCode() : 0);
      result = 31 * result + port;
      return result;
   }

}
