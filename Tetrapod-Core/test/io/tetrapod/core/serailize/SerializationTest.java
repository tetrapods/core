package io.tetrapod.core.serailize;

import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.datasources.TempBufferDataSource;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.identity.IdentityContract;

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class SerializationTest {

   @Test
   public void testWebRoute() throws Exception {
      WebRoute[] routes = new IdentityContract().getWebRoutes();
      for (WebRoute w : routes)
         rinseTempBuff(w);
      AddWebRoutesRequest awr = new AddWebRoutesRequest(routes);
      assertTrue(rinseTempBuff(awr));
   }
   
   public boolean rinseTempBuff(Structure s1) throws Exception {
      TempBufferDataSource temp = TempBufferDataSource.forWriting();
      s1.write(temp);
      byte[] data1 = Arrays.copyOfRange(temp.rawBuffer(), 0, temp.rawCount());
      
      temp = temp.toReading(); 
      Structure s2 = s1.getClass().newInstance();
      s2.read(temp);
      
      TempBufferDataSource temp2 = TempBufferDataSource.forWriting();
      s2.write(temp2);
      byte[] data2 = Arrays.copyOfRange(temp2.rawBuffer(), 0, temp2.rawCount());
      
      return Arrays.equals(data1,  data2);
   }
}
