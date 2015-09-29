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
public class ServiceRequestStatsRequest extends Request {

   public static final int STRUCT_ID = 16134423;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
   
   public ServiceRequestStatsRequest() {
      defaults();
   }

   public ServiceRequestStatsRequest(String domain, int limit, long minTime, RequestStatsSort sortBy) {
      this.domain = domain;
      this.limit = limit;
      this.minTime = minTime;
      this.sortBy = sortBy;
   }   

   /**
    * null is default RPC domain
    */
   public String domain;
   public int limit;
   public long minTime;
   public RequestStatsSort sortBy;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      domain = null;
      limit = 0;
      minTime = 0;
      sortBy = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.domain);
      data.write(2, this.limit);
      data.write(3, this.minTime);
      if (this.sortBy != null) data.write(4, this.sortBy.value);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.domain = data.read_string(tag); break;
            case 2: this.limit = data.read_int(tag); break;
            case 3: this.minTime = data.read_long(tag); break;
            case 4: this.sortBy = RequestStatsSort.from(data.read_int(tag)); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return ServiceRequestStatsRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return ServiceRequestStatsRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestServiceRequestStats(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestServiceRequestStats(ServiceRequestStatsRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[4+1];
      result[1] = "domain";
      result[2] = "limit";
      result[3] = "minTime";
      result[4] = "sortBy";
      return result;
   }
   
   public final Structure make() {
      return new ServiceRequestStatsRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "ServiceRequestStatsRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }

}
