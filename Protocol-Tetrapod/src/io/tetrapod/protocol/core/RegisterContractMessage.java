package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.serialize.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.TypeDescriptor;
import io.tetrapod.protocol.core.StructDescription;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class RegisterContractMessage extends Message {
   
   public static final int STRUCT_ID = 11935907;
   public static final int CONTRACT_ID = TetrapodContract.CONTRACT_ID;
    
   public RegisterContractMessage() {
      defaults();
   }

   public RegisterContractMessage(ContractDescription info) {
      this.info = info;
   }   
   
   public ContractDescription info;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      info = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      if (this.info != null) data.write(1, this.info);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.info = data.read_struct(tag, new ContractDescription()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return RegisterContractMessage.CONTRACT_ID;
   }

   public final int getStructId() {
      return RegisterContractMessage.STRUCT_ID;
   }
   
   @Override
   public final void dispatch(SubscriptionAPI api, MessageContext ctx) {
      if (api instanceof Handler)
         ((Handler)api).messageRegisterContract(this, ctx);
      else
         api.genericMessage(this, ctx);
   }
   
   public static interface Handler extends SubscriptionAPI {
      void messageRegisterContract(RegisterContractMessage m, MessageContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[1+1];
      result[1] = "info";
      return result;
   }
   
   public final Structure make() {
      return new RegisterContractMessage();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();     
      desc.name = "RegisterContractMessage";
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_STRUCT, ContractDescription.CONTRACT_ID, ContractDescription.STRUCT_ID);
      return desc;
   }
}
