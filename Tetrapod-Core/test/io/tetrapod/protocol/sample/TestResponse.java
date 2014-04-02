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
public class TestResponse extends Response {
   
   public static final int STRUCT_ID = 117;
   public static final int CONTRACT_ID = SampleContract.CONTRACT_ID;
    
   public TestResponse() {
      defaults();
   }

   public TestResponse(int intval, double doubleval, boolean boolval, long longval, byte byteval, String stringval, int[] intarray, List<Integer> intlist, TestInfo info, List<TestInfo> infolist, TestInfo[] infoarray) {
      this.intval = intval;
      this.doubleval = doubleval;
      this.boolval = boolval;
      this.longval = longval;
      this.byteval = byteval;
      this.stringval = stringval;
      this.intarray = intarray;
      this.intlist = intlist;
      this.info = info;
      this.infolist = infolist;
      this.infoarray = infoarray;
   }   
   
   public int intval;
   public double doubleval;
   public boolean boolval;
   public long longval;
   public byte byteval;
   public String stringval;
   public int[] intarray;
   public List<Integer> intlist;
   public TestInfo info;
   public List<TestInfo> infolist;
   public TestInfo[] infoarray;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      intval = 1;
      doubleval = 3.4;
      boolval = true;
      longval = 500;
      byteval = 127;
      stringval = "happy\" da\\ys # //";
      intarray = null;
      intlist = null;
      info = null;
      infolist = null;
      infoarray = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.intval);
      data.write(2, this.doubleval);
      data.write(3, this.boolval);
      data.write(4, this.longval);
      data.write(5, this.byteval);
      data.write(6, this.stringval);
      if (this.intarray != null) data.write(7, this.intarray);
      if (this.intlist != null) data.write_int(8, this.intlist);
      if (this.info != null) data.write(9, this.info);
      if (this.infolist != null) data.write_struct(10, this.infolist);
      if (this.infoarray != null) data.write(11, this.infoarray);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.intval = data.read_int(tag); break;
            case 2: this.doubleval = data.read_double(tag); break;
            case 3: this.boolval = data.read_boolean(tag); break;
            case 4: this.longval = data.read_long(tag); break;
            case 5: this.byteval = data.read_byte(tag); break;
            case 6: this.stringval = data.read_string(tag); break;
            case 7: this.intarray = data.read_int_array(tag); break;
            case 8: this.intlist = data.read_int_list(tag); break;
            case 9: this.info = data.read_struct(tag, new TestInfo()); break;
            case 10: this.infolist = data.read_struct_list(tag, new TestInfo()); break;
            case 11: this.infoarray = data.read_struct_array(tag, new TestInfo()); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
  
   public final int getContractId() {
      return TestResponse.CONTRACT_ID;
   }

   public final int getStructId() {
      return TestResponse.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[11+1];
      result[1] = "intval";
      result[2] = "doubleval";
      result[3] = "boolval";
      result[4] = "longval";
      result[5] = "byteval";
      result[6] = "stringval";
      result[7] = "intarray";
      result[8] = "intlist";
      result[9] = "info";
      result[10] = "infolist";
      result[11] = "infoarray";
      return result;
   }

   public final Structure make() {
      return new TestResponse();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_DOUBLE, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_BYTE, 0, 0);
      desc.types[6] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[7] = new TypeDescriptor(TypeDescriptor.T_INT_LIST, 0, 0);
      desc.types[8] = new TypeDescriptor(TypeDescriptor.T_INT_LIST, 0, 0);
      desc.types[9] = new TypeDescriptor(TypeDescriptor.T_STRUCT, TestInfo.CONTRACT_ID, TestInfo.STRUCT_ID);
      desc.types[10] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, TestInfo.CONTRACT_ID, TestInfo.STRUCT_ID);
      desc.types[11] = new TypeDescriptor(TypeDescriptor.T_STRUCT_LIST, TestInfo.CONTRACT_ID, TestInfo.STRUCT_ID);
      return desc;
   }
 }
