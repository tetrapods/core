package io.tetrapod.core.serailize;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

import io.tetrapod.core.json.JSONObject;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import io.tetrapod.core.serialize.datasources.*;
import io.tetrapod.protocol.core.*;

public class SerializationTest {

   public static enum EnumType implements Enum_int<EnumType> {
      A(1), B(2), C(3);

      public static EnumType from(int val) {
         for (EnumType e : EnumType.values())
            if (e.value == (val))
               return e;
         return null;
      }

      public final int value;

      private EnumType(int value) {
         this.value = value;
      }

      public int getValue() {
         return value;
      }
   }

   public static class StructWithEnum extends Structure {

      public static final int STRUCT_ID   = 12279721;
      public static final int CONTRACT_ID = 7;

      public StructWithEnum() {
         defaults();
      }

      public StructWithEnum(int[] ids, EnumType[] types) {
         this.ids = ids;
         this.types = types;
      }

      public int[]      ids;
      public EnumType[] types;

      public final void defaults() {
         ids = null;
         types = null;
      }

      @Override
      public final void write(DataSource data) throws IOException {
         if (this.ids != null)
            data.write(1, this.ids);
         if (this.types != null)
            data.write(2, this.types);
         data.writeEndTag();
      }

      @Override
      public final void read(DataSource data) throws IOException {
         defaults();
         while (true) {
            int tag = data.readTag();
            switch (tag) {
               case 1:
                  this.ids = data.read_int_array(tag);
                  break;
               case 2:
                  this.types = data.read_enum_int_array(tag, EnumType.class);
                  break;
               case Codec.END_TAG:
                  return;
               default:
                  data.skip(tag);
                  break;
            }
         }
      }

      public final int getContractId() {
         return StructWithEnum.CONTRACT_ID;
      }

      public final int getStructId() {
         return StructWithEnum.STRUCT_ID;
      }

      public final String[] tagWebNames() {
         String[] result = new String[4 + 1];
         result[1] = "ids";
         result[4] = "types";
         return result;
      }

      public final Structure make() {
         return new StructWithEnum();
      }

      public final StructDescription makeDescription() {
         StructDescription desc = new StructDescription();
         desc.name = "StructWithEnum";
         desc.tagWebNames = tagWebNames();
         desc.types = new TypeDescriptor[desc.tagWebNames.length];
         desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
         desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT_LIST, 0, 0);
         desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT_LIST, 0, 0);
         return desc;
      }
   }

   @Test
   public void testBasicRinse() throws Exception {
      WebRoute[] routes = new TetrapodContract().getWebRoutes();
      for (WebRoute w : routes)
         assertTrue(rinseTempBuff(w));
      AddServiceInformationRequest awr = new AddServiceInformationRequest(new ContractDescription());
      awr.info.routes = routes;
      assertTrue(rinseTempBuff(awr));
   }

   @Test
   public void testBasicRinse2() throws Exception {
      StructWithEnum vlr = new StructWithEnum(new int[] { 1, 2, 3 }, new EnumType[] { EnumType.A, EnumType.B, EnumType.C });
      assertTrue("buff", rinseTempBuff(vlr));
      assertTrue("json", rinseJSONBuff(vlr));
      assertTrue("adapter", rinseViaAdapter(vlr));
   }

   @Test
   public void testDynamicRinse() throws Exception {
      new TetrapodContract().registerStructs();
      WebRoute[] routes = new TetrapodContract().getWebRoutes();
      for (WebRoute w : routes)
         assertTrue(rinseViaAdapter(w));
      AddServiceInformationRequest awr = new AddServiceInformationRequest(new ContractDescription());
      awr.info.routes = routes;
      assertTrue(rinseViaAdapter(awr));
   }

   @Test
   public void testNullInStructList() throws Exception {
      new CoreContract().registerStructs();
      StructDescription t = new StructDescription(
            new TypeDescriptor[] { new TypeDescriptor(), new TypeDescriptor(), null, new TypeDescriptor(), }, null, null);
      assertTrue(rinseTempBuff(t));
      assertTrue(rinseViaAdapter(t));
      assertTrue(rinseJSONBuff(t));
   }

   @Test
   public void testLong() throws Exception {
      new TetrapodContract().registerStructs();
      RaftStatsResponse m = new RaftStatsResponse((byte) 12, 0x20E1D00AEDF8DBEDL, 0, 0x20E1D00AEDF8DBEDL, 0x20E1D00AEDF8DBEDL, 42, null);
      assertTrue("tempBuff", rinseTempBuff(m));
      assertTrue("adapter", rinseViaAdapter(m));
      assertTrue("json", rinseJSONBuff(m));
   }

   public boolean rinseTempBuff(Structure s1) throws Exception {
      TempBufferDataSource temp = TempBufferDataSource.forWriting();
      s1.write(temp);
      byte[] data1 = Arrays.copyOfRange(temp.rawBuffer(), 0, temp.rawCount());
      temp = temp.toReading();
      Structure s2 = s1.make();
      s2.read(temp);

      TempBufferDataSource temp2 = TempBufferDataSource.forWriting();
      s2.write(temp2);
      byte[] data2 = Arrays.copyOfRange(temp2.rawBuffer(), 0, temp2.rawCount());
      return printDiffs(data1, data2, s1, s2);
   }

   public boolean rinseJSONBuff(Structure s1) throws Exception {
      JSONDataSource temp = new JSONDataSource();
      s1.write(temp);
      JSONDataSource tempR = new JSONDataSource(new JSONObject(temp.getJSON().toString(3)));
      Structure s2 = s1.make();
      s2.read(tempR);

      JSONDataSource temp2 = new JSONDataSource();
      s2.write(temp2);

      String str1 = temp.getJSON().toString(3);
      String str2 = temp2.getJSON().toString(3);
      if (str1.equals(str2))
         return true;
      System.err.println(str1);
      System.err.println(str2);
      return false;
   }

   public boolean rinseViaAdapter(Structure s1) throws Exception {
      TempBufferDataSource temp = TempBufferDataSource.forWriting();
      s1.write(temp);
      byte[] data1 = Arrays.copyOfRange(temp.rawBuffer(), 0, temp.rawCount());

      temp = temp.toReading();
      Structure s2 = new StructureAdapter(s1.makeDescription());
      s2.read(temp);

      TempBufferDataSource temp2 = TempBufferDataSource.forWriting();
      s2.write(temp2);
      byte[] data2 = Arrays.copyOfRange(temp2.rawBuffer(), 0, temp2.rawCount());

      return printDiffs(data1, data2, s1, s2);
   }

   private boolean printDiffs(byte[] data1, byte[] data2, Structure s1, Structure s2) {
      if (Arrays.equals(data1, data2))
         return true;
      int len = data1.length;
      int len2 = data2.length;
      if (len != len2)
         System.err.printf("lengths differ %d != %d\n", len, len2);
      len = Math.min(len, len2);
      for (int i = 0; i < len; i++) {
         if (data1[i] != data2[i])
            System.err.printf("vals differ at %d, %d != %d\n", i, data1[i], data2[i]);
      }
      System.err.println(s1.dump());
      System.err.println(s2.dump());
      return false;
   }

}
