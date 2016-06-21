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

/**
 * An error in any one of the build commands stops processing the remaining ones
 */

@SuppressWarnings("all")
public class ExecuteBuildCommandRequest extends Request {

   public static final int STRUCT_ID = 7902304;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   
   public ExecuteBuildCommandRequest() {
      defaults();
   }

   public ExecuteBuildCommandRequest(int accountId, String authToken, List<BuildCommand> commands) {
      this.accountId = accountId;
      this.authToken = authToken;
      this.commands = commands;
   }   

   public int accountId;
   public String authToken;
   public List<BuildCommand> commands;

   public final Structure.Security getSecurity() {
      return Security.ADMIN;
   }

   public final void defaults() {
      accountId = 0;
      authToken = null;
      commands = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.accountId);
      data.write(2, this.authToken);
      if (this.commands != null) data.write_struct(3, this.commands);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.accountId = data.read_int(tag); break;
            case 2: this.authToken = data.read_string(tag); break;
            case 3: this.commands = data.read_struct_list(tag, new BuildCommand()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return ExecuteBuildCommandRequest.CONTRACT_ID;
   }

   public final int getStructId() {
      return ExecuteBuildCommandRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestExecuteBuildCommand(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestExecuteBuildCommand(ExecuteBuildCommandRequest r, RequestContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "accountId";
      result[2] = "authToken";
      result[3] = "commands";
      return result;
   }
   
   public final Structure make() {
      return new ExecuteBuildCommandRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "ExecuteBuildCommandRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, BuildCommand.CONTRACT_ID, BuildCommand.STRUCT_ID);
      return desc;
   }

   public final Response securityCheck(RequestContext ctx) {
      return ctx.securityCheck(this, accountId, authToken);
   }
      
   protected boolean isSensitive(String fieldName) {
      if (fieldName.equals("authToken")) return true;
      return false;
   }
}
