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
public class IssueCommandRequest extends RequestWithResponse<IssueCommandResponse> {

   public static final int STRUCT_ID = 9938264;
   public static final int CONTRACT_ID = RaftContract.CONTRACT_ID;
   
   public IssueCommandRequest() {
      defaults();
   }

   public IssueCommandRequest(int type, byte[] command) {
      this.type = type;
      this.command = command;
   }   

   public int type;
   public byte[] command;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      type = 0;
      command = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.type);
      if (this.command != null) data.write(2, this.command);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.type = data.read_int(tag); break;
            case 2: this.command = data.read_byte_array(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return IssueCommandRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return IssueCommandRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestIssueCommand(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestIssueCommand(IssueCommandRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "type";
      result[2] = "command";
      return result;
   }
   
   public final Structure make() {
      return new IssueCommandRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "IssueCommandRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_BYTE_LIST, 0, 0);
      return desc;
   }

}
