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
public class HostInfoResponse extends Response {
   
   public static final int STRUCT_ID = 7161106;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
    
   public HostInfoResponse() {
      defaults();
   }

   public HostInfoResponse(String hostname, byte numCores, String meta) {
      this.hostname = hostname;
      this.numCores = numCores;
      this.meta = meta;
   }   
   
   public String hostname;
   public byte numCores;
   
   /**
    * optional json meta data (can contain os stuff, aws info, etc...)
    */
   public String meta;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      hostname = null;
      numCores = 0;
      meta = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.hostname);
      data.write(2, this.numCores);
      data.write(3, this.meta);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.hostname = data.read_string(tag); break;
            case 2: this.numCores = data.read_byte(tag); break;
            case 3: this.meta = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return HostInfoResponse.CONTRACT_ID;
   }

   public final int getStructId() {
      return HostInfoResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[1] = "hostname";
      result[2] = "numCores";
      result[3] = "meta";
      return result;
   }

   public final Structure make() {
      return new HostInfoResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "HostInfoResponse";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_BYTE, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }
 }
