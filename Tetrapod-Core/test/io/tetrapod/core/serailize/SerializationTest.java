package io.tetrapod.core.serailize;

import static org.junit.Assert.assertTrue;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.StructureAdapter;
import io.tetrapod.core.serialize.datasources.*;
import io.tetrapod.protocol.core.*;

import java.util.Arrays;

import org.junit.Test;

public class SerializationTest {

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
      StructDescription t = new StructDescription(new TypeDescriptor[] {
            new TypeDescriptor(),
            new TypeDescriptor(),
            null,
            new TypeDescriptor(),
      }, null);
      assertTrue(rinseTempBuff(t));
      assertTrue(rinseViaAdapter(t));
      assertTrue(rinseJSONBuff(t));
   }

   @Test
   public void testLong() throws Exception {
      new TetrapodContract().registerStructs();
      EntityRegisteredMessage m = new EntityRegisteredMessage(new Entity(11, 12,  0x20E1D00AEDF8DBEDL, "xxx", 13, (byte)14, "name", 15, 16, 17));
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
      if (Arrays.equals(data1,  data2))
         return true;
      int len = data1.length;
      int len2 = data2.length;
      if (len != len2)
         System.err.printf("lengths differ %d != %d\n", len, len2);
      len = Math.min(len, len2);
      for (int i =0; i < len; i++) {
         if (data1[i] != data2[i])
            System.err.printf("vals differ at %d, %d != %d\n", i, data1[i], data2[i]);
      }
      System.err.println(s1.dump());
      System.err.println(s2.dump());
      return false;
   }

}
