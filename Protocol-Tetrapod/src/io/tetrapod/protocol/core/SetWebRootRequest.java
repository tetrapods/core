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

   public SetWebRootRequest(String logicalName, String webRootFolder, String hostname) {
      this.logicalName = logicalName;
      this.webRootFolder = webRootFolder;
      this.hostname = hostname;
   }   

   public String logicalName;
   public String webRootFolder;
   public String hostname;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      logicalName = null;
      webRootFolder = null;
      hostname = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.logicalName);
      data.write(2, this.webRootFolder);
      data.write(3, this.hostname);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.logicalName = data.read_string(tag); break;
            case 2: this.webRootFolder = data.read_string(tag); break;
            case 3: this.hostname = data.read_string(tag); break;
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
      String[] result = new String[3+1];
      result[1] = "logicalName";
      result[2] = "webRootFolder";
      result[3] = "hostname";
      return result;
   }
   
   public final Structure make() {
      return new SetWebRootRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

}
