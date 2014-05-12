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
public class AddWebFileRequest extends Request {

   public static final int STRUCT_ID = 5158759;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   
   public AddWebFileRequest() {
      defaults();
   }

   public AddWebFileRequest(String path, String webRootName, byte[] contents, boolean clearBeforAdding) {
      this.path = path;
      this.webRootName = webRootName;
      this.contents = contents;
      this.clearBeforAdding = clearBeforAdding;
   }   

   public String path;
   public String webRootName;
   public byte[] contents;
   public boolean clearBeforAdding;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      path = null;
      webRootName = null;
      contents = null;
      clearBeforAdding = false;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.path);
      data.write(2, this.webRootName);
      if (this.contents != null) data.write(3, this.contents);
      data.write(4, this.clearBeforAdding);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.path = data.read_string(tag); break;
            case 2: this.webRootName = data.read_string(tag); break;
            case 3: this.contents = data.read_byte_array(tag); break;
            case 4: this.clearBeforAdding = data.read_boolean(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return AddWebFileRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return AddWebFileRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestAddWebFile(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestAddWebFile(AddWebFileRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[4+1];
      result[1] = "path";
      result[2] = "webRootName";
      result[3] = "contents";
      result[4] = "clearBeforAdding";
      return result;
   }
   
   public final Structure make() {
      return new AddWebFileRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_BYTE_LIST, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      return desc;
   }

}
