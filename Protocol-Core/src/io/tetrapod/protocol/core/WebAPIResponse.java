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
public class WebAPIResponse extends Response {
   
   public static final int STRUCT_ID = 9652194;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
    
   public WebAPIResponse() {
      defaults();
   }

   public WebAPIResponse(String json, String redirect) {
      this.json = json;
      this.redirect = redirect;
   }   
   
   public String json;
   public String redirect;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      json = null;
      redirect = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.json);
      data.write(2, this.redirect);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.json = data.read_string(tag); break;
            case 2: this.redirect = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return WebAPIResponse.CONTRACT_ID;
   }

   public final int getStructId() {
      return WebAPIResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "json";
      result[2] = "redirect";
      return result;
   }

   public final Structure make() {
      return new WebAPIResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      return desc;
   }
 }
