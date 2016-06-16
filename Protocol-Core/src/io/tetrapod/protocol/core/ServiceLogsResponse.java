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
public class ServiceLogsResponse extends Response {
   
   public static final int STRUCT_ID = 6345878;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
    
   public ServiceLogsResponse() {
      defaults();
   }

   public ServiceLogsResponse(long lastLogId, List<ServiceLogEntry> items) {
      this.lastLogId = lastLogId;
      this.items = items;
   }   
   
   public long lastLogId;
   public List<ServiceLogEntry> items;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      lastLogId = 0;
      items = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.lastLogId);
      if (this.items != null) data.write_struct(2, this.items);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.lastLogId = data.read_long(tag); break;
            case 2: this.items = data.read_struct_list(tag, new ServiceLogEntry()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return ServiceLogsResponse.CONTRACT_ID;
   }

   public final int getStructId() {
      return ServiceLogsResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "lastLogId";
      result[2] = "items";
      return result;
   }

   public final Structure make() {
      return new ServiceLogsResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "ServiceLogsResponse";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, ServiceLogEntry.CONTRACT_ID, ServiceLogEntry.STRUCT_ID);
      return desc;
   }
 }
