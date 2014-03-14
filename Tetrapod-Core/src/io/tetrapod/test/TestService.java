package io.tetrapod.test;

// uncomment after code generating
/*
import java.io.IOException;

import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;

public class TestService implements ITestService {

   @Override
   public Response genericRequest(Request r) {
      System.out.println("generic subclass called");
      return null;
   }

   @Override
   public Response request(PrimTestRequest req) {
      System.out.println("proper subclass called");
      return null;
   }
   
   @Override
   public Response request(StructTestRequest r) {
      System.out.println("proper subclass called");
      return null;
   }

   @Override
   public Response request(CollPrimTestRequest r) {
      System.out.println("proper subclass called");
      return null;
   }

   public static void main(String[] args) {
      IService s = new TestService();
      Request r1 = new PrimTestRequest();
      Request r2 = new StructTestRequest();
      Request r3 = new Request() {
         public void write(DataSource data) throws IOException {}
         public void read(DataSource data) throws IOException {}
         public int getStructId() { return 0; }
      };
      r1.dispatch(s);
      r2.dispatch(s);
      r3.dispatch(s);
   }


}
*/