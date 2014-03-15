package io.tetrapod.core.serialize.datasources;

import java.io.ByteArrayOutputStream;

public class TempBufferDataSource extends IOStreamDataSource {

   private static class MyByteArrayOutputStream extends ByteArrayOutputStream {
      byte[] rawBuffer() {
         return buf;
      }
      int rawCount() {
         return count;
      }
   }
   
   private MyByteArrayOutputStream myOutStream;
   
   public TempBufferDataSource() {
      this.myOutStream = new MyByteArrayOutputStream();
      this.out = myOutStream;
   }
   
   public byte[] rawBuffer() {
      return myOutStream.rawBuffer();
   }
   
   public int rawCount() {
      return myOutStream.rawCount();
   }
   
   public void reset() {
      myOutStream.reset();
   }
   
}
