package io.tetrapod.core.serialize.datasources;

import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.*;

import java.io.*;

/**
 * A datasource based on streaming. This is basically our wire protocol although the actual
 * streaming of bytes is left to subclasses.
 * <p>
 * This does not support random access by tag.
 * 
 * @author fortin
 */
abstract public class StreamDatasource implements DataSource {

   private static final int TYPE_VAR_INT      = 0;
   private static final int TYPE_VAR_LONG     = 1;
   private static final int TYPE_LENGTH_DELIM = 2;
   private static final int TYPE_FIXED_8BIT   = 3;
   private static final int TYPE_FIXED_32BIT  = 4;
   private static final int TYPE_FIXED_64BIT  = 5;

   private static final int CONTINUE          = 0b1000_0000;
   private static final int MASK              = 0b0111_1111;

   private int              lastTagType       = 0;

   @Override
   public int readTag() throws IOException {
      int tagAndType = readVarInt();
      lastTagType = tagAndType & 0b111;
      return tagAndType >>> 3;
   }

   @Override
   public String read_string(int tag) throws IOException {
      int len = readVarInt();
      return new String(readRawBytes(len), "UTF-8");
   }

   @Override
   public int read_int(int tag) throws IOException {
      return readVarInt();
   }

   @Override
   public byte read_byte(int tag) throws IOException {
      return (byte)readRawByte();
   }

   @Override
   public long read_long(int tag) throws IOException {
      return readVarLong();
   }

   @Override
   public double read_double(int tag) throws IOException {
      long res = (long)readRawByte();
      res |= ((long)readRawByte()) << 8;
      res |= ((long)readRawByte()) << 16;
      res |= ((long)readRawByte()) << 24;
      res |= ((long)readRawByte()) << 32;
      res |= ((long)readRawByte()) << 40;
      res |= ((long)readRawByte()) << 48;
      res |= ((long)readRawByte()) << 56;
      return Double.longBitsToDouble(res);
   }

   @Override
   public boolean read_boolean(int tag) throws IOException {
      return readRawByte() == 0;
   }
   
   @Override
   public <T extends Structure> T read_struct(int tag, Class<T> structClass) throws IOException {
      @SuppressWarnings("unused")
      int len = readVarInt();
      try {
         T inst = structClass.newInstance();
         inst.read(this);
         return inst;
      } catch (InstantiationException | IllegalAccessException e) {
         throw new IOException("cannont instantiate class", e);
      }
   }

   @Override
   public void write(int tag, int intval) throws IOException {
      writeTag(tag, TYPE_VAR_INT);
      writeVarInt(intval);

   }

   @Override
   public void write(int tag, byte byteval) throws IOException {
      writeTag(tag, TYPE_FIXED_8BIT);
      writeRawByte(byteval);
   }

   @Override
   public void write(int tag, double doubleval) throws IOException {
      writeTag(tag, TYPE_FIXED_64BIT);
      long value = Double.doubleToRawLongBits(doubleval);
      writeRawByte((int)(value) & 0xFF);
      writeRawByte((int)(value >> 8) & 0xFF);
      writeRawByte((int)(value >> 16) & 0xFF);
      writeRawByte((int)(value >> 24) & 0xFF);
      writeRawByte((int)(value >> 32) & 0xFF);
      writeRawByte((int)(value >> 40) & 0xFF);
      writeRawByte((int)(value >> 48) & 0xFF);
      writeRawByte((int)(value >> 56) & 0xFF);
   }

   @Override
   public void write(int tag, long longval) throws IOException {
      writeTag(tag, TYPE_VAR_LONG);
      writeVarLong(longval);
   }

   @Override
   public void write(int tag, boolean boolval) throws IOException {
      writeTag(tag, TYPE_FIXED_8BIT);
      writeRawByte(boolval ? 1 : 0);
   }

   @Override
   public void write(int tag, String stringval) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      final byte[] bytes = stringval.getBytes("UTF-8");
      writeVarInt(bytes.length);
      writeRawBytes(bytes);
   }
   
   @Override
   public <T extends Structure> void write(int tag, T struct) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      // OPTIMIZE: eliminate some of this byte copying
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      IOStreamDataSource ds = IOStreamDataSource.forWriting(out);
      struct.write(ds);
      writeVarInt(out.size());
      writeRawBytes(out.toByteArray());
   }

   @Override
   public void skip(int tag) throws IOException {
      int bytesToSkip = 0;
      switch (lastTagType) {
         case TYPE_FIXED_32BIT:
            bytesToSkip = 4;
            break;

         case TYPE_FIXED_64BIT:
            bytesToSkip = 8;
            break;

         case TYPE_FIXED_8BIT:
            bytesToSkip = 1;
            break;

         case TYPE_VAR_INT:
            readVarInt();
            return;

         case TYPE_VAR_LONG:
            readVarLong();
            return;

         case TYPE_LENGTH_DELIM:
            bytesToSkip = readVarInt();
            break;
      }
      for (int i = 0; i < bytesToSkip; i++)
         readRawByte();
   }

   @Override
   public void writeEndTag() throws IOException {
      writeTag(Codec.END_TAG, TYPE_FIXED_8BIT);
   }

   private void writeTag(int tag, int type) throws IOException {
      int t = (tag << 2) | (type & 0b11);
      writeRawByte(t);
   }

   protected void writeVarInt(int x) throws IOException {
      boolean keepGoing = true;
      while (keepGoing) {
         int v = x & MASK;
         keepGoing = v != x;
         if (keepGoing) {
            v = v | CONTINUE;
            x = x >>> 7;
         }
         writeRawByte(v);
      }
   }

   protected void writeVarLong(long x) throws IOException {
      boolean keepGoing = true;
      while (keepGoing) {
         int v = (int)(x & MASK);
         keepGoing = v != x;
         if (keepGoing) {
            v = v | CONTINUE;
            x = x >>> 7;
         }
         writeRawByte(v);
      }
   }

   protected int readVarInt() throws IOException {
      int x = 0;
      int shift = 0;
      while (true) {
         int v = readRawByte();
         x = x | ((v & MASK) << shift);
         if ((v & CONTINUE) == 0)
            return x;
         shift += 7;
      }
   }

   protected long readVarLong() throws IOException {
      long x = 0;
      int shift = 0;
      while (true) {
         int v = readRawByte();
         x = x | ((v & MASK) << shift);
         if ((v & CONTINUE) == 0)
            return x;
         shift += 7;
      }
   }

   abstract protected int readRawByte() throws IOException;

   abstract protected byte[] readRawBytes(int len) throws IOException;

   abstract protected void writeRawByte(int val) throws IOException;

   abstract protected void writeRawBytes(byte[] vals) throws IOException;
}
