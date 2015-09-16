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
public class ServiceRequestStatsResponse extends Response {
   
   public static final int STRUCT_ID = 6312573;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
    
   public ServiceRequestStatsResponse() {
      defaults();
   }

   public ServiceRequestStatsResponse(List<RequestStat> requests, long minTime) {
      this.requests = requests;
      this.minTime = minTime;
   }   
   
   public List<RequestStat> requests;
   
   /**
    * last timestamp in stats buffer
    */
   public long minTime;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      requests = null;
      minTime = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      if (this.requests != null) data.write_struct(1, this.requests);
      data.write(2, this.minTime);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.requests = data.read_struct_list(tag, new RequestStat()); break;
            case 2: this.minTime = data.read_long(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return ServiceRequestStatsResponse.CONTRACT_ID;
   }

   public final int getStructId() {
      return ServiceRequestStatsResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "requests";
      result[2] = "minTime";
      return result;
   }

   public final Structure make() {
      return new ServiceRequestStatsResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, RequestStat.CONTRACT_ID, RequestStat.STRUCT_ID);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      return desc;
   }
 }
