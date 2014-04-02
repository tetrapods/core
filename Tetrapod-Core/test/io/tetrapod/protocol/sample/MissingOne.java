package io.tetrapod.protocol.sample;

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
public class MissingOne extends Structure {
   
   public static final int STRUCT_ID = 10024719;
   public static final int CONTRACT_ID = SampleContract.CONTRACT_ID;
    
   public MissingOne() {
      defaults();
   }

   public MissingOne(TestInfo[] tis, int otherField) {
      this.tis = tis;
      this.otherField = otherField;
   }   
   
   public TestInfo[] tis;
   public int otherField;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      tis = null;
      otherField = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      if (this.tis != null) data.write(2, this.tis);
      data.write(3, this.otherField);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 2: this.tis = data.read_struct_array(tag, new TestInfo()); break;
            case 3: this.otherField = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return MissingOne.CONTRACT_ID;
   }

   public final int getStructId() {
      return MissingOne.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[3+1];
      result[2] = "tis";
      result[3] = "otherField";
      return result;
   }

   public final Structure make() {
      return new MissingOne();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, TestInfo.CONTRACT_ID, TestInfo.STRUCT_ID);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      return desc;
   }
}
