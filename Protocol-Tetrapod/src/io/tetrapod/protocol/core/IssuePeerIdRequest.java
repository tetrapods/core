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
public class IssuePeerIdRequest extends Request {

   public static final int STRUCT_ID = 10809624;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   
   public IssuePeerIdRequest() {
      defaults();
   }

   public IssuePeerIdRequest(String host, int clusterPort) {
      this.host = host;
      this.clusterPort = clusterPort;
   }   

   public String host;
   public int clusterPort;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      host = null;
      clusterPort = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.host);
      data.write(2, this.clusterPort);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.host = data.read_string(tag); break;
            case 2: this.clusterPort = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return IssuePeerIdRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return IssuePeerIdRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestIssuePeerId(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestIssuePeerId(IssuePeerIdRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "host";
      result[2] = "clusterPort";
      return result;
   }
   
   public final Structure make() {
      return new IssuePeerIdRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }

}
