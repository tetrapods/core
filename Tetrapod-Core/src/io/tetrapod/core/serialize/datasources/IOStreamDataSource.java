package io.tetrapod.core.serialize.datasources;

import java.io.*;

public class IOStreamDataSource extends StreamDatasource {

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

   private InputStream  in;
   private OutputStream out;

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
   protected void writeRawBytes(byte[] vals) throws IOException {
      out.write(vals);
   }

}
