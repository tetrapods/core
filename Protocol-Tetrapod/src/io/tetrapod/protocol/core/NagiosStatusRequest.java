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
public class NagiosStatusRequest extends Request {

   public static final int STRUCT_ID = 12047571;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   
   public NagiosStatusRequest() {
      defaults();
   }

   public NagiosStatusRequest(String adminToken, String hostname, boolean toggle) {
      this.adminToken = adminToken;
      this.hostname = hostname;
      this.toggle = toggle;
   }   

   public String adminToken;
   public String hostname;
   public boolean toggle;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      adminToken = null;
      hostname = null;
      toggle = false;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.adminToken);
      data.write(2, this.hostname);
      data.write(3, this.toggle);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.adminToken = data.read_string(tag); break;
            case 2: this.hostname = data.read_string(tag); break;
            case 3: this.toggle = data.read_boolean(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return NagiosStatusRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return NagiosStatusRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestNagiosStatus(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestNagiosStatus(NagiosStatusRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "adminToken";
      result[2] = "hostname";
      result[3] = "toggle";
      return result;
   }
   
   public final Structure make() {
      return new NagiosStatusRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "NagiosStatusRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      return desc;
   }

}
