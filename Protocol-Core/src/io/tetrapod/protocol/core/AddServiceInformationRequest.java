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
public class AddServiceInformationRequest extends Request {

   public static final int STRUCT_ID = 14381454;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   
   public AddServiceInformationRequest() {
      defaults();
   }

   public AddServiceInformationRequest(WebRoute[] routes, List<StructDescription> structs) {
      this.routes = routes;
      this.structs = structs;
   }   

   public WebRoute[] routes;
   
   /**
    * structs that could possibly be used in end user comms
    */
   public List<StructDescription> structs;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      routes = null;
      structs = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      if (this.routes != null) data.write(1, this.routes);
      if (this.structs != null) data.write_struct(2, this.structs);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.routes = data.read_struct_array(tag, new WebRoute()); break;
            case 2: this.structs = data.read_struct_list(tag, new StructDescription()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return AddServiceInformationRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return AddServiceInformationRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestAddServiceInformation(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestAddServiceInformation(AddServiceInformationRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "routes";
      result[2] = "structs";
      return result;
   }
   
   public final Structure make() {
      return new AddServiceInformationRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, WebRoute.CONTRACT_ID, WebRoute.STRUCT_ID);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, StructDescription.CONTRACT_ID, StructDescription.STRUCT_ID);
      return desc;
   }

}
