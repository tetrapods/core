package io.tetrapod.protocol.web;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.Admin;
import io.tetrapod.core.serialize.*;
import io.tetrapod.protocol.core.TypeDescriptor;
import io.tetrapod.protocol.core.StructDescription;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("all")
public class KeepAliveRequest extends Request {

   public static final int STRUCT_ID = 5512920;
   public static final int CONTRACT_ID = WebContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = WebContract.SUB_CONTRACT_ID;

   public KeepAliveRequest() {
      defaults();
   }

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return KeepAliveRequest.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return KeepAliveRequest.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return KeepAliveRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestKeepAlive(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestKeepAlive(KeepAliveRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[0+1];
      
      return result;
   }
   
   public final Structure make() {
      return new KeepAliveRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "KeepAliveRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      
      return desc;
   }

}
