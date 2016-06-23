package io.tetrapod.protocol.raft;

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
public class AppendEntriesRequest extends RequestWithResponse<AppendEntriesResponse> {

   public static final int STRUCT_ID = 5018018;
   public static final int CONTRACT_ID = RaftContract.CONTRACT_ID;
   
   public AppendEntriesRequest() {
      defaults();
   }

   public AppendEntriesRequest(long term, int leaderId, long prevLogIndex, long prevLogTerm, LogEntry[] entries, long leaderCommit) {
      this.term = term;
      this.leaderId = leaderId;
      this.prevLogIndex = prevLogIndex;
      this.prevLogTerm = prevLogTerm;
      this.entries = entries;
      this.leaderCommit = leaderCommit;
   }   

   public long term;
   public int leaderId;
   public long prevLogIndex;
   public long prevLogTerm;
   public LogEntry[] entries;
   public long leaderCommit;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      term = 0;
      leaderId = 0;
      prevLogIndex = 0;
      prevLogTerm = 0;
      entries = null;
      leaderCommit = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.term);
      data.write(2, this.leaderId);
      data.write(3, this.prevLogIndex);
      data.write(4, this.prevLogTerm);
      if (this.entries != null) data.write(5, this.entries);
      data.write(6, this.leaderCommit);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.term = data.read_long(tag); break;
            case 2: this.leaderId = data.read_int(tag); break;
            case 3: this.prevLogIndex = data.read_long(tag); break;
            case 4: this.prevLogTerm = data.read_long(tag); break;
            case 5: this.entries = data.read_struct_array(tag, new LogEntry()); break;
            case 6: this.leaderCommit = data.read_long(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return AppendEntriesRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return AppendEntriesRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestAppendEntries(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestAppendEntries(AppendEntriesRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[6+1];
      result[1] = "term";
      result[2] = "leaderId";
      result[3] = "prevLogIndex";
      result[4] = "prevLogTerm";
      result[5] = "entries";
      result[6] = "leaderCommit";
      return result;
   }
   
   public final Structure make() {
      return new AppendEntriesRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "AppendEntriesRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, LogEntry.CONTRACT_ID, LogEntry.STRUCT_ID);
      desc.types[6] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      return desc;
   }

}
