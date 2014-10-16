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
public class ServiceErrorLogsResponse extends Response {
   
   public static final int STRUCT_ID = 9302372;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
    
   public ServiceErrorLogsResponse() {
      defaults();
   }

   public ServiceErrorLogsResponse(long lastLogId, List<ServiceLogEntry> errors, List<ServiceLogEntry> warnings) {
      this.lastLogId = lastLogId;
      this.errors = errors;
      this.warnings = warnings;
   }   
   
   public long lastLogId;
   public List<ServiceLogEntry> errors;
   public List<ServiceLogEntry> warnings;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      lastLogId = 0;
      errors = null;
      warnings = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.lastLogId);
      if (this.errors != null) data.write_struct(2, this.errors);
      if (this.warnings != null) data.write_struct(3, this.warnings);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.lastLogId = data.read_long(tag); break;
            case 2: this.errors = data.read_struct_list(tag, new ServiceLogEntry()); break;
            case 3: this.warnings = data.read_struct_list(tag, new ServiceLogEntry()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return ServiceErrorLogsResponse.CONTRACT_ID;
   }

   public final int getStructId() {
      return ServiceErrorLogsResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "lastLogId";
      result[2] = "errors";
      result[3] = "warnings";
      return result;
   }

   public final Structure make() {
      return new ServiceErrorLogsResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, ServiceLogEntry.CONTRACT_ID, ServiceLogEntry.STRUCT_ID);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, ServiceLogEntry.CONTRACT_ID, ServiceLogEntry.STRUCT_ID);
      return desc;
   }
 }
