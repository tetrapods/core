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
public class ServiceLogEntry extends Structure {
   
   public static final byte LEVEL_ALL = 0; 
   public static final byte LEVEL_TRACE = 10; 
   public static final byte LEVEL_DEBUG = 20; 
   public static final byte LEVEL_INFO = 30; 
   public static final byte LEVEL_WARN = 40; 
   public static final byte LEVEL_ERROR = 50; 
   public static final byte LEVEL_OFF = 100; 
   
   public static final int STRUCT_ID = 11222968;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = CoreContract.SUB_CONTRACT_ID;

   public ServiceLogEntry() {
      defaults();
   }

   public ServiceLogEntry(String msg, byte level, long timestamp, String thread, String logger, String contextId) {
      this.msg = msg;
      this.level = level;
      this.timestamp = timestamp;
      this.thread = thread;
      this.logger = logger;
      this.contextId = contextId;
   }   
   
   public String msg;
   public byte level;
   public long timestamp;
   public String thread;
   public String logger;
   public String contextId;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      msg = null;
      level = 0;
      timestamp = 0;
      thread = null;
      logger = null;
      contextId = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.msg);
      data.write(2, this.level);
      data.write(3, this.timestamp);
      data.write(4, this.thread);
      data.write(5, this.logger);
      data.write(6, this.contextId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.msg = data.read_string(tag); break;
            case 2: this.level = data.read_byte(tag); break;
            case 3: this.timestamp = data.read_long(tag); break;
            case 4: this.thread = data.read_string(tag); break;
            case 5: this.logger = data.read_string(tag); break;
            case 6: this.contextId = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   public final int getContractId() {
      return ServiceLogEntry.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return ServiceLogEntry.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return ServiceLogEntry.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[6+1];
      result[1] = "msg";
      result[2] = "level";
      result[3] = "timestamp";
      result[4] = "thread";
      result[5] = "logger";
      result[6] = "contextId";
      return result;
   }

   public final Structure make() {
      return new ServiceLogEntry();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "ServiceLogEntry";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_BYTE, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[6] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }

   @Override
   @SuppressWarnings("RedundantIfStatement")
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      ServiceLogEntry that = (ServiceLogEntry) o;

      if (msg != null ? !msg.equals(that.msg) : that.msg != null)
         return false;
      if (level != that.level)
         return false;
      if (timestamp != that.timestamp)
         return false;
      if (thread != null ? !thread.equals(that.thread) : that.thread != null)
         return false;
      if (logger != null ? !logger.equals(that.logger) : that.logger != null)
         return false;
      if (contextId != null ? !contextId.equals(that.contextId) : that.contextId != null)
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = 0;
      result = 31 * result + (msg != null ? msg.hashCode() : 0);
      result = 31 * result + level;
      result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
      result = 31 * result + (thread != null ? thread.hashCode() : 0);
      result = 31 * result + (logger != null ? logger.hashCode() : 0);
      result = 31 * result + (contextId != null ? contextId.hashCode() : 0);
      return result;
   }

}
