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
public class ServiceDetailsResponse extends Response {
   
   public static final int STRUCT_ID = 12435407;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = CoreContract.SUB_CONTRACT_ID;

   public ServiceDetailsResponse() {
      defaults();
   }

   public ServiceDetailsResponse(String iconURL, String metadata, ServiceCommand[] commands) {
      this.iconURL = iconURL;
      this.metadata = metadata;
      this.commands = commands;
   }   
   
   public String iconURL;
   public String metadata;
   public ServiceCommand[] commands;

   public final Structure.Security getSecurity() {
      return Security.ADMIN;
   }

   public final void defaults() {
      iconURL = null;
      metadata = null;
      commands = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.iconURL);
      data.write(3, this.metadata);
      if (this.commands != null) data.write(2, this.commands);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.iconURL = data.read_string(tag); break;
            case 3: this.metadata = data.read_string(tag); break;
            case 2: this.commands = data.read_struct_array(tag, new ServiceCommand()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return ServiceDetailsResponse.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return ServiceDetailsResponse.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return ServiceDetailsResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "iconURL";
      result[3] = "metadata";
      result[2] = "commands";
      return result;
   }

   public final Structure make() {
      return new ServiceDetailsResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "ServiceDetailsResponse";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, ServiceCommand.CONTRACT_ID, ServiceCommand.STRUCT_ID);
      return desc;
   }
 }
