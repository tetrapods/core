package io.tetrapod.core.serialize.datasources;

import java.io.*;

/**
 * A reusable data source that gives access to its internal buffer.  Used in
 * the streaming protocol as a temporary destination for variable length fields
 * so that the byte count can be prefixed to the field data.
 * <p>
 * This datasource can only be used as a write target.
 */
public class TempBufferDataSource extends IOStreamDataSource {

   private static class MyByteArrayOutputStream extends ByteArrayOutputStream {
      byte[] rawBuffer() {
         return buf;
      }
      int rawCount() {
         return count;
      }
   }
   
   private static class MyByteArrayInputStream extends ByteArrayInputStream {
      public MyByteArrayInputStream(byte[] b, int off, int len) {
         super(b, off, len);
      }
      
      byte[] rawBuffer() {
         return buf;
      }
      int rawCount() {
         return count;
      }
      int rawPos() {
         return pos;
      }
   }
   
   private MyByteArrayOutputStream myOutStream;
   private MyByteArrayInputStream myInStream;
   
   public static TempBufferDataSource forWriting() {
      TempBufferDataSource t = new TempBufferDataSource();
      t.myOutStream = new MyByteArrayOutputStream();
      t.out = t.myOutStream;
      return t;
   }

   public static TempBufferDataSource forReading(byte[] buf, int offset, int length) {
      TempBufferDataSource t = new TempBufferDataSource();
      t.myInStream = new MyByteArrayInputStream(buf, offset, length);
      t.in = t.myInStream;
      return t;
   }
   
   private TempBufferDataSource() {}

   public byte[] rawBuffer() {
      return myInStream == null ? myOutStream.rawBuffer() : myInStream.rawBuffer();
   }
   
   public int rawCount() {
      return myInStream == null ? myOutStream.rawCount() : myInStream.rawCount();
   }

   public int rawPos() {
      return myInStream == null ? 0 : myInStream.rawPos();
   }

   public void reset() {
      myOutStream.reset();
   }
   
   public TempBufferDataSource toReading() {
      return TempBufferDataSource.forReading(rawBuffer(), 0, rawCount());
   }

   
}
