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
public class ServiceLogsRequest extends RequestWithResponse<ServiceLogsResponse> {

   public static final int STRUCT_ID = 13816458;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
   
   public ServiceLogsRequest() {
      defaults();
   }

   public ServiceLogsRequest(long logId, byte level, int maxItems) {
      this.logId = logId;
      this.level = level;
      this.maxItems = maxItems;
   }   

   public long logId;
   public byte level;
   public int maxItems;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      logId = 0;
      level = 0;
      maxItems = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.logId);
      data.write(2, this.level);
      data.write(3, this.maxItems);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.logId = data.read_long(tag); break;
            case 2: this.level = data.read_byte(tag); break;
            case 3: this.maxItems = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return ServiceLogsRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return ServiceLogsRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestServiceLogs(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestServiceLogs(ServiceLogsRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "logId";
      result[2] = "level";
      result[3] = "maxItems";
      return result;
   }
   
   public final Structure make() {
      return new ServiceLogsRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "ServiceLogsRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_BYTE, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }

}
