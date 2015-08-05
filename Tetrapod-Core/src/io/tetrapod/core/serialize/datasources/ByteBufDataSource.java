package io.tetrapod.core.serialize.datasources;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * A binary protocol datasource to/from a netty ByteBuf. 
 */
public class ByteBufDataSource extends StreamDataSource {

   public ByteBufDataSource(ByteBuf buf) {
      this.buffer = buf;
   }

   private ByteBuf buffer;

   @Override
   protected int readRawByte() throws IOException {
      return buffer.readUnsignedByte();
   }

   @Override
   public String read_string(int tag) throws IOException {
      int len = readVarInt();
      
      if (len == 1) {
         byte b = buffer.readByte();
         if (b == 0)
            return null;
         return new String(new byte[] { b }, "UTF-8");
      }
      
      String s = buffer.toString(buffer.readerIndex(), len, Charset.forName("UTF-8"));
      buffer.skipBytes(len);
      return s;
   }

   @Override
   protected byte[] readRawBytes(int len) throws IOException {
      byte[] res = new byte[len];
      try {
         buffer.readBytes(res);
      } catch (IndexOutOfBoundsException e) {
         throw new IOException(e);
      }
      return res;
   }

   @Override
   protected void writeRawByte(int val) throws IOException {
      buffer.writeByte(val);
   }

   @Override
   protected void writeRawBytes(byte[] vals, int offset, int count) throws IOException {
      buffer.writeBytes(vals, offset, count);
   }
   
   @Override
   public void writeVarInt(int x) throws IOException {
      super.writeVarInt(x);
   }
   
   @Override
   public int readVarInt() throws IOException {
      return super.readVarInt();
   }

}
