package io.tetrapod.core.serialize.datasources;

import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.*;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

/**
 * A datasource based on streaming. This is basically our wire protocol although the actual
 * streaming of bytes is left to subclasses.
 * <p>
 * This does not support random access by tag.
 * 
 * @author fortin
 */
abstract public class StreamDatasource implements DataSource {

   private static final int        TYPE_VAR_INT      = 0;
   private static final int        TYPE_VAR_LONG     = 1;
   private static final int        TYPE_LENGTH_DELIM = 2;
   private static final int        TYPE_FIXED_8BIT   = 3;
   private static final int        TYPE_FIXED_32BIT  = 4;
   private static final int        TYPE_FIXED_64BIT  = 5;

   private static final int        CONTINUE          = 0b1000_0000;
   private static final int        MASK              = 0b0111_1111;

   private int                     lastTagType       = 0;
   private TempBufferDataSource    tempBuffer        = null;

   @Override
   public int readTag() throws IOException {
      int tagAndType = readVarInt();
      lastTagType = tagAndType & 0b111;
      return tagAndType >>> 3;
   }

   @Override
   public String read_string(int tag) throws IOException {
      int len = readVarInt();
      return len == 0 ? "" : new String(readRawBytes(len), "UTF-8");
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

   public int[] read_int_array(int tag) throws IOException {
      readVarInt(); // byte length
      int len = readVarInt();
      int[] array = new int[len];
      for (int i = 0; i < len; i++) {
         array[i] = readVarInt();
      }
      return array;
   }
   
   public List<Integer> read_int_list(int tag) throws IOException {
      int len = readVarInt();
      List<Integer> list = new ArrayList<>();
      for (int i = 0; i < len; i++) {
         list.add(readVarInt());
      }
      return list;
   }

   public void write(int tag, int[] array) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      temp.writeVarInt(array.length);
      for (int i = 0; i < array.length; i++) {
         temp.writeVarInt(array[i]);
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }
   
   public void write_int(int tag, List<Integer> list) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      temp.writeVarInt(list.size());
      for (int i = 0; i < list.size(); i++) {
         temp.writeVarInt(list.get(i));
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }

   public long[] read_long_array(int tag) throws IOException {
      readVarInt(); // byte length
      int len = readVarInt();
      long[] array = new long[len];
      for (int i = 0; i < len; i++) {
         array[i] = readVarLong();
      }
      return array;
   }
   
   public List<Long> read_long_list(int tag) throws IOException {
      int len = readVarInt();
      List<Long> list = new ArrayList<>();
      for (int i = 0; i < len; i++) {
         list.add(readVarLong());
      }
      return list;
   }

   public void write(int tag, long[] array) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      temp.writeVarInt(array.length);
      for (int i = 0; i < array.length; i++) {
         temp.writeVarLong(array[i]);
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }
   
   public void write_long(int tag, List<Long> list) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      temp.writeVarInt(list.size());
      for (int i = 0; i < list.size(); i++) {
         temp.writeVarLong(list.get(i));
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }

   public byte[] read_byte_array(int tag) throws IOException {
      int len = readVarInt();
      return readRawBytes(len);
   }
   
   public List<Byte> read_byte_list(int tag) throws IOException {
      int len = readVarInt();
      List<Byte> list = new ArrayList<>();
      for (int i = 0; i < len; i++) {
         list.add((byte)readRawByte());
      }
      return list;
   }

   public void write(int tag, byte[] array) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      writeVarInt(array.length);
      writeRawBytes(array);
   }
   
   public void write_byte(int tag, List<Byte> list) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      writeVarInt(list.size());
      for (int i = 0; i < list.size(); i++) {
         writeRawByte(list.get(i));
      }
   }

   public boolean[] read_boolean_array(int tag) throws IOException {
      int len = readVarInt();
      boolean[] array = new boolean[len];
      for (int i = 0; i < len; i++) {
         array[i] = readRawByte() == 1;
      }
      return array;
   }
   
   public List<Boolean> read_boolean_list(int tag) throws IOException {
      int len = readVarInt();
      List<Boolean> list = new ArrayList<>();
      for (int i = 0; i < len; i++) {
         list.add(readRawByte() == 1);
      }
      return list;
   }

   public void write(int tag, boolean[] array) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      writeVarInt(array.length);
      for (int i = 0; i < array.length; i++) {
         writeRawByte(array[i] ? 1 : 0);
      }
   }
   
   public void write_boolean(int tag, List<Boolean> list) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      writeVarInt(list.size());
      for (int i = 0; i < list.size(); i++) {
         writeRawByte(list.get(i) ? 1 : 0);
      }
   }

   public double[] read_double_array(int tag) throws IOException {
      int len = readVarInt() / 8;
      double[] array = new double[len];
      for (int i = 0; i < len; i++) {
         array[i] = read_double(0);
      }
      return array;
   }
   
   public List<Double> read_double_list(int tag) throws IOException {
      int len = readVarInt();
      List<Double> list = new ArrayList<>();
      for (int i = 0; i < len; i++) {
         list.add(read_double(0));
      }
      return list;
   }

   public void write(int tag, double[] array) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      writeVarInt(array.length * 8);
      for (int i = 0; i < array.length; i++) {
         writeDoubleNoTag(array[i]);
      }
   }
   
   public void write_double(int tag, List<Double> list) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      writeVarInt(list.size());
      for (int i = 0; i < list.size(); i++) {
         writeDoubleNoTag(list.get(i));
      }
   }

   public String[] read_string_array(int tag) throws IOException {
      readVarInt(); // byte length
      int len = readVarInt();
      String[] array = new String[len];
      for (int i = 0; i < len; i++) {
         array[i] = read_string(0);
      }
      return array;
   }
   
   public List<String> read_string_list(int tag) throws IOException {
      int len = readVarInt();
      List<String> list = new ArrayList<>();
      for (int i = 0; i < len; i++) {
         list.add(read_string(0));
      }
      return list;
   }

   public void write(int tag, String[] array) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      temp.writeVarInt(array.length * 8);
      for (int i = 0; i < array.length; i++) {
         temp.writeStringNoTag(array[i]);
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }
   
   public void write_string(int tag, List<String> list) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      temp.writeVarInt(list.size());
      for (int i = 0; i < list.size(); i++) {
         temp.writeStringNoTag(list.get(i));
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }

   public <T extends Structure> T[] read_struct_array(int tag, Class<T> structClass) throws IOException {
      readVarInt(); // byte length
      int len = readVarInt();
      @SuppressWarnings("unchecked")
      T[] array = (T[])Array.newInstance(structClass, len);
      for (int i = 0; i < len; i++) {
         array[i] = read_struct(0, structClass);
      }
      return array;
   }
   
   public  <T extends Structure> List<T> read_struct_list(int tag, Class<T> structClass) throws IOException {
      int len = readVarInt();
      List<T> list = new ArrayList<>();
      for (int i = 0; i < len; i++) {
         list.add( read_struct(0, structClass));
      }
      return list;
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
      writeDoubleNoTag(doubleval);
   }

   protected void writeDoubleNoTag(double doubleval) throws IOException {
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
      writeStringNoTag(stringval);
   }

   protected void writeStringNoTag(String stringval) throws IOException {
      if (stringval == null) {
         writeVarInt(0);
         return;
      }
      final byte[] bytes = stringval.getBytes("UTF-8");
      writeVarInt(bytes.length);
      writeRawBytes(bytes);
   }

   @Override
   public <T extends Structure> void write(int tag, T struct) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      struct.write(temp);
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }
   
   public <T extends Structure> void write(int tag, T[] array) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      temp.writeVarInt(array.length * 8);
      for (int i = 0; i < array.length; i++) {
         array[i].write(temp);
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }

   public <T extends Structure> void write_struct(int tag, List<T> list) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      temp.writeVarInt(list.size() * 8);
      for (int i = 0; i < list.size(); i++) {
         list.get(i).write(temp);
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
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
      int t = (tag << 3) | (type & 0b111);
      writeVarInt(t);
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

   protected void writeRawBytes(byte[] vals) throws IOException {
      writeRawBytes(vals, 0, vals.length);
   }

   abstract protected void writeRawBytes(byte[] vals, int offset, int count) throws IOException;
   
   protected TempBufferDataSource getTempBuffer() {
      if (tempBuffer == null) 
         tempBuffer = new TempBufferDataSource();
      return tempBuffer;
   }

}
