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
public class SendWebRootRequest extends Request {

   public static final int STRUCT_ID = 16081718;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   
   public SendWebRootRequest() {
      defaults();
   }

   public SendWebRootRequest(String webRootName) {
      this.webRootName = webRootName;
   }   

   public String webRootName;

   public final Structure.Security getSecurity() {
      return Security.PRIVATE;
   }

   public final void defaults() {
      webRootName = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.webRootName);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.webRootName = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return SendWebRootRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return SendWebRootRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestSendWebRoot(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestSendWebRoot(SendWebRootRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[1+1];
      result[1] = "webRootName";
      return result;
   }
   
   public final Structure make() {
      return new SendWebRootRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

}
