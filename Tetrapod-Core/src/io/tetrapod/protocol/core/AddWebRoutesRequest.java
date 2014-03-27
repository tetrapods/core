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
public class AddWebRoutesRequest extends Request {

   public static final int STRUCT_ID = 9719643;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   
   public AddWebRoutesRequest() {
      defaults();
   }

   public AddWebRoutesRequest(WebRoute[] routes) {
      this.routes = routes;
   }   

   public WebRoute[] routes;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      routes = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      if (this.routes != null) data.write(1, this.routes);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.routes = data.read_struct_array(tag, new WebRoute()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return AddWebRoutesRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return AddWebRoutesRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestAddWebRoutes(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestAddWebRoutes(AddWebRoutesRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[1+1];
      result[1] = "routes";
      return result;
   }
   
   public final Structure make() {
      return new AddWebRoutesRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, WebRoute.CONTRACT_ID, WebRoute.STRUCT_ID);
      return desc;
   }
}
