package io.tetrapod.core.serialize.datasources;

import java.io.*;

/**
 * Implement the streaming binary protocol to/from java IOStreams.  Supports
 * read-only, write-only, or read-write streams. 
 */
public class IOStreamDataSource extends StreamDataSource {

   public static IOStreamDataSource forReading(InputStream in) {
      return forReadWrite(in, null);
   }

   public static IOStreamDataSource forWriting(OutputStream out) {
      return forReadWrite(null, out);
   }

   public static IOStreamDataSource forReadWrite(InputStream in, OutputStream out) {
      IOStreamDataSource d = new IOStreamDataSource();
      d.in = in;
      d.out = out;
      return d;
   }

   protected InputStream  in;
   protected OutputStream out;

   @Override
   protected int readRawByte() throws IOException {
      return in.read();
   }

   @Override
   protected byte[] readRawBytes(int len) throws IOException {
      byte[] b = new byte[len];
      in.read(b);
      return b;
   }

   @Override
   protected void writeRawByte(int val) throws IOException {
      out.write(val);
   }

   @Override
   protected void writeRawBytes(byte[] vals, int offset, int count) throws IOException {
      out.write(vals, offset, count);
   }

}
