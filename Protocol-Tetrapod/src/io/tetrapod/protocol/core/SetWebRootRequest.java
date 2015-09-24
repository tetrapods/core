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
public class SetWebRootRequest extends Request {

   public static final int STRUCT_ID = 4029010;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   
   public SetWebRootRequest() {
      defaults();
   }

   public SetWebRootRequest(String adminToken, WebRootDef def) {
      this.adminToken = adminToken;
      this.def = def;
   }   

   public String adminToken;
   public WebRootDef def;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      adminToken = null;
      def = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.adminToken);
      if (this.def != null) data.write(2, this.def);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.adminToken = data.read_string(tag); break;
            case 2: this.def = data.read_struct(tag, new WebRootDef()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return SetWebRootRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return SetWebRootRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestSetWebRoot(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestSetWebRoot(SetWebRootRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "adminToken";
      result[2] = "def";
      return result;
   }
   
   public final Structure make() {
      return new SetWebRootRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "SetWebRootRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRUCT, WebRootDef.CONTRACT_ID, WebRootDef.STRUCT_ID);
      return desc;
   }

   protected boolean isSensitive(String fieldName) {
      if (fieldName.equals("adminToken")) return true;
      return false;
   }
}
