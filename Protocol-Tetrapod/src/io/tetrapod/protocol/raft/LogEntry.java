package io.tetrapod.protocol.raft;

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
public class LogEntry extends Structure {
   
   public static final int STRUCT_ID = 3294185;
   public static final int CONTRACT_ID = RaftContract.CONTRACT_ID;
    
   public LogEntry() {
      defaults();
   }

   public LogEntry(long term, long index, int type, byte[] command) {
      this.term = term;
      this.index = index;
      this.type = type;
      this.command = command;
   }   
   
   public long term;
   public long index;
   public int type;
   public byte[] command;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      term = 0;
      index = 0;
      type = 0;
      command = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.term);
      data.write(2, this.index);
      data.write(3, this.type);
      if (this.command != null) data.write(4, this.command);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.term = data.read_long(tag); break;
            case 2: this.index = data.read_long(tag); break;
            case 3: this.type = data.read_int(tag); break;
            case 4: this.command = data.read_byte_array(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return LogEntry.CONTRACT_ID;
   }

   public final int getStructId() {
      return LogEntry.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[4+1];
      result[1] = "term";
      result[2] = "index";
      result[3] = "type";
      result[4] = "command";
      return result;
   }

   public final Structure make() {
      return new LogEntry();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.name = "LogEntry";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_BYTE_LIST, 0, 0);
      return desc;
   }
}
