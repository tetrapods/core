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
public class WebAPIRequest extends Request<WebAPIResponse> {

   public static final int STRUCT_ID = 9321342;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
   
   public WebAPIRequest() {
      defaults();
   }

   public WebAPIRequest(String route, String headers, String params, String body, String uri) {
      this.route = route;
      this.headers = headers;
      this.params = params;
      this.body = body;
      this.uri = uri;
   }   

   /**
    * route name
    */
   public String route;
   
   /**
    * json string
    */
   public String headers;
   
   /**
    * json string
    */
   public String params;
   
   /**
    * json string
    */
   public String body;
   public String uri;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      route = null;
      headers = null;
      params = null;
      body = null;
      uri = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.route);
      data.write(2, this.headers);
      data.write(3, this.params);
      data.write(4, this.body);
      data.write(5, this.uri);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.route = data.read_string(tag); break;
            case 2: this.headers = data.read_string(tag); break;
            case 3: this.params = data.read_string(tag); break;
            case 4: this.body = data.read_string(tag); break;
            case 5: this.uri = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return WebAPIRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return WebAPIRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestWebAPI(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestWebAPI(WebAPIRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[5+1];
      result[1] = "route";
      result[2] = "headers";
      result[3] = "params";
      result[4] = "body";
      result[5] = "uri";
      return result;
   }
   
   public final Structure make() {
      return new WebAPIRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "WebAPIRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

}
