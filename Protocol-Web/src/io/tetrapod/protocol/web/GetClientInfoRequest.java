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
public class GetClientInfoRequest extends RequestWithResponse<GetClientInfoResponse> {

   public static final int STRUCT_ID = 3498983;
   public static final int CONTRACT_ID = WebContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = WebContract.SUB_CONTRACT_ID;

   public GetClientInfoRequest() {
      defaults();
   }

   public GetClientInfoRequest(int clientId) {
      this.clientId = clientId;
   }   

   public int clientId;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      clientId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.clientId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.clientId = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return GetClientInfoRequest.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return GetClientInfoRequest.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return GetClientInfoRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestGetClientInfo(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestGetClientInfo(GetClientInfoRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[1+1];
      result[1] = "clientId";
      return result;
   }
   
   public final Structure make() {
      return new GetClientInfoRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "GetClientInfoRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }

}
