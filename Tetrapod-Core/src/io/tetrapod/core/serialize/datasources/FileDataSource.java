package io.tetrapod.core.serialize.datasources;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;

public class FileDataSource extends StreamDataSource {

   public static FileDataSource forReading(String filename) throws IOException {
      return forReading(FileSystems.getDefault().getPath(filename));
   }
      
   public static FileDataSource forReading(Path path) throws IOException {
      return new FileDataSource(FileChannel.open(path, StandardOpenOption.READ));
   }

   public static FileDataSource forAppending(String filename) throws IOException {
      return forAppending(FileSystems.getDefault().getPath(filename));
   }
   
   public static FileDataSource forAppending(Path path) throws IOException {
      return new FileDataSource(FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE));
   }

   public FileDataSource(FileChannel b) {
      this.channel = b;
   }

   private FileChannel channel;
   private ByteBuffer single = ByteBuffer.allocate(1);
   
   public boolean atEnd() throws IOException {
      return channel.position() >= channel.size();
   }
   
   public void close() throws IOException {
      channel.close();
   }
   
   public void flush() throws IOException {
      channel.force(false);
   }

   @Override
   protected int readRawByte() throws IOException {
      channel.read(single);
      single.rewind();
      return (int)single.get(0) & 0xFF;
   }

   @Override
   protected byte[] readRawBytes(int len) throws IOException {
      byte[] res = new byte[len];
      ByteBuffer b = ByteBuffer.wrap(res);
      try {
         channel.read(b);
         
      } catch (IndexOutOfBoundsException e) {
         throw new IOException(e);
      }
      return res;
   }

   @Override
   protected void writeRawByte(int val) throws IOException {
      single.rewind();
      single.put(0, (byte)(val));
      channel.write(single);
   }

   @Override
   protected void writeRawBytes(byte[] vals, int offset, int count) throws IOException {
      ByteBuffer b = ByteBuffer.wrap(vals, offset, count);
      channel.write(b);
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
