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
public class VoteRequest extends Request {

   public static final int STRUCT_ID = 9348108;
   public static final int CONTRACT_ID = RaftContract.CONTRACT_ID;
   
   public VoteRequest() {
      defaults();
   }

   public VoteRequest(String clusterName, long term, int candidateId, long lastLogIndex, long lastLogTerm) {
      this.clusterName = clusterName;
      this.term = term;
      this.candidateId = candidateId;
      this.lastLogIndex = lastLogIndex;
      this.lastLogTerm = lastLogTerm;
   }   

   public String clusterName;
   public long term;
   public int candidateId;
   public long lastLogIndex;
   public long lastLogTerm;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      clusterName = null;
      term = 0;
      candidateId = 0;
      lastLogIndex = 0;
      lastLogTerm = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.clusterName);
      data.write(2, this.term);
      data.write(3, this.candidateId);
      data.write(4, this.lastLogIndex);
      data.write(5, this.lastLogTerm);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.clusterName = data.read_string(tag); break;
            case 2: this.term = data.read_long(tag); break;
            case 3: this.candidateId = data.read_int(tag); break;
            case 4: this.lastLogIndex = data.read_long(tag); break;
            case 5: this.lastLogTerm = data.read_long(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return VoteRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return VoteRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestVote(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestVote(VoteRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[5+1];
      result[1] = "clusterName";
      result[2] = "term";
      result[3] = "candidateId";
      result[4] = "lastLogIndex";
      result[5] = "lastLogTerm";
      return result;
   }
   
   public final Structure make() {
      return new VoteRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      return desc;
   }

}
