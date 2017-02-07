package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.Admin;
import io.tetrapod.core.RequestClass;
import io.tetrapod.core.RoutedValueProvider;
import io.tetrapod.core.tasks.Task;
import io.tetrapod.core.serialize.*;
import io.tetrapod.protocol.core.TypeDescriptor;
import io.tetrapod.protocol.core.StructDescription;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("all")
public class InternalSetClusterPropertyRequest extends Request  {

   public static final int STRUCT_ID = 15539010;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
   public static final int SUB_CONTRACT_ID = TetrapodContract.SUB_CONTRACT_ID;

   public InternalSetClusterPropertyRequest() {
      defaults();
   }

   public InternalSetClusterPropertyRequest(ClusterProperty property) {
      this.property = property;
   }   

   public ClusterProperty property;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      property = null;
   }

   @Override
   public final void write(DataSource data) throws IOException {
      if (this.property != null) data.write(1, this.property);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.property = data.read_struct(tag, new ClusterProperty()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return InternalSetClusterPropertyRequest.CONTRACT_ID;
   }

   public final int getSubContractId() {
      return InternalSetClusterPropertyRequest.SUB_CONTRACT_ID;
   }

   public final int getStructId() {
      return InternalSetClusterPropertyRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestInternalSetClusterProperty(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestInternalSetClusterProperty(InternalSetClusterPropertyRequest r, RequestContext ctx);
   }

   public static interface Handler2 {
      @RequestClass(InternalSetClusterPropertyRequest.class)
      Task<Response> internalSetClusterProperty(ClusterProperty property);
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[1+1];
      result[1] = "property";
      return result;
   }
   
   public final Structure make() {
      return new InternalSetClusterPropertyRequest();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();      
      desc.name = "InternalSetClusterPropertyRequest";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRUCT, ClusterProperty.CONTRACT_ID, ClusterProperty.STRUCT_ID);
      return desc;
   }

}
