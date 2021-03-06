package io.tetrapod.core.serialize.datasources;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;

/**
 * A datasource based on streaming. This is basically the wire protocol although the actual streaming of bytes is left to subclasses.
 * 
 * @author fortin
 */
abstract public class StreamDataSource implements DataSource {

   private static final int     TYPE_VAR_INT      = 0;
   private static final int     TYPE_VAR_LONG     = 1;
   private static final int     TYPE_LENGTH_DELIM = 2;
   private static final int     TYPE_FIXED_8BIT   = 3;
   private static final int     TYPE_FIXED_32BIT  = 4;
   private static final int     TYPE_FIXED_64BIT  = 5;
   private static final int     TYPE_NULL         = 6;

   private static final int     CONTINUE          = 0b1000_0000;
   private static final int     MASK              = 0b0111_1111;

   private int                  lastTagType       = 0;
   private TempBufferDataSource tempBuffer        = null;

   @Override
   public int readTag() throws IOException {
      int tagAndType = readVarInt();
      lastTagType = tagAndType & 0b111;
      return tagAndType >>> 3;
   }

   @Override
   public String read_string(int tag) throws IOException {
      int len = readVarInt();
      if (len == 0)
         return "";
      byte[] bytes = readRawBytes(len);
      if (bytes.length == 1 && bytes[0] == 0)
         return null;
      return new String(bytes, "UTF-8");
   }

   @Override
   public int read_int(int tag) throws IOException {
      return readVarInt();
   }

   @Override
   public byte read_byte(int tag) throws IOException {
      return (byte) readRawByte();
   }

   @Override
   public long read_long(int tag) throws IOException {
      return readVarLong();
   }

   @Override
   public double read_double(int tag) throws IOException {
      long res = (long) readRawByte();
      res |= ((long) readRawByte()) << 8;
      res |= ((long) readRawByte()) << 16;
      res |= ((long) readRawByte()) << 24;
      res |= ((long) readRawByte()) << 32;
      res |= ((long) readRawByte()) << 40;
      res |= ((long) readRawByte()) << 48;
      res |= ((long) readRawByte()) << 56;
      return Double.longBitsToDouble(res);
   }

   @Override
   public boolean read_boolean(int tag) throws IOException {
      return readRawByte() == 1;
   }

   @Override
   public <T extends Structure> T read_struct(int tag, T struct) throws IOException {
      readVarInt(); // byte length
      struct.read(this);
      return struct;
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
      readVarInt(); // byte length
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
      readVarInt(); // byte length
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
         list.add((byte) readRawByte());
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
      int len = readVarInt() / 8;
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
      writeVarInt(list.size() * 8);
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
      readVarInt(); // byte length
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
      temp.writeVarInt(array.length);
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

   @SuppressWarnings("unchecked")
   public <T extends Structure> T[] read_struct_array(int tag, T struct) throws IOException {
      readVarInt(); // byte length
      int len = readVarInt();
      T[] array = (T[]) Array.newInstance(struct.getClass(), len);
      for (int i = 0; i < len; i++) {
         T inst = i == 0 ? struct : (T) struct.make();
         inst.read(this);
         array[i] = lastWasNull() ? null : inst;
      }
      return array;
   }

   @SuppressWarnings("unchecked")
   public <T extends Structure> List<T> read_struct_list(int tag, T struct) throws IOException {
      readVarInt(); // byte length
      int len = readVarInt();
      List<T> list = new ArrayList<>();
      for (int i = 0; i < len; i++) {
         T inst = i == 0 ? struct : (T) struct.make();
         inst.read(this);
         list.add(lastWasNull() ? null : inst);
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
      writeRawByte((int) (value) & 0xFF);
      writeRawByte((int) (value >> 8) & 0xFF);
      writeRawByte((int) (value >> 16) & 0xFF);
      writeRawByte((int) (value >> 24) & 0xFF);
      writeRawByte((int) (value >> 32) & 0xFF);
      writeRawByte((int) (value >> 40) & 0xFF);
      writeRawByte((int) (value >> 48) & 0xFF);
      writeRawByte((int) (value >> 56) & 0xFF);
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
         writeVarInt(1);
         writeRawByte(0);
         return;
      }
      if (stringval.isEmpty()) {
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
      temp.writeVarInt(array.length);
      for (int i = 0; i < array.length; i++) {
         if (array[i] == null)
            temp.writeNullTag();
         else
            array[i].write(temp);
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }

   public <T extends Structure> void write_struct(int tag, List<T> list) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      temp.writeVarInt(list.size());
      for (int i = 0; i < list.size(); i++) {
         if (list.get(i) == null)
            temp.writeNullTag();
         else
            list.get(i).write(temp);
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }

   @SuppressWarnings("unchecked")
   public <T extends Flags_int<T>> T[] read_flags_int_array(int tag, T struct) throws IOException {
      readVarInt(); // byte length
      int len = readVarInt();
      T[] array = (T[]) Array.newInstance(struct.getClass(), len);
      for (int i = 0; i < len; i++) {
         T inst = i == 0 ? struct : (T) struct.make();
         array[i] = inst.set(readVarInt());
      }
      return array;
   }

   @SuppressWarnings("unchecked")
   public <T extends Flags_long<T>> T[] read_flags_long_array(int tag, T struct) throws IOException {
      readVarInt(); // byte length
      int len = readVarInt();
      T[] array = (T[]) Array.newInstance(struct.getClass(), len);
      for (int i = 0; i < len; i++) {
         T inst = i == 0 ? struct : (T) struct.make();
         array[i] = inst.set(readVarLong());
      }
      return array;
   }

   @Override
   public void write(int tag, Flags_int<?>[] array) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      temp.writeVarInt(array.length);
      for (int i = 0; i < array.length; i++) {
         temp.writeVarInt(array[i] == null ? 0 : array[i].value);
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }

   @Override
   public void write(int tag, Flags_long<?>[] array) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      temp.writeVarInt(array.length);
      for (int i = 0; i < array.length; i++) {
         temp.writeVarLong(array[i] == null ? 0 : array[i].value);
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T extends Enum_int<T>> T[] read_enum_int_array(int tag, Class<T> c) throws IOException {
      readVarInt(); // byte length
      int len = readVarInt();
      T[] array = (T[]) Array.newInstance(c, len);
      try {
         Method m = c.getMethod("from", int.class);
         for (int i = 0; i < len; i++) {
            array[i] = (T) m.invoke(null, readVarInt());
         }
      } catch (Exception e) {
         throw new IOException(e);
      }
      return array;
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T extends Enum_String<T>> T[] read_enum_string_array(int tag, Class<T> c) throws IOException {
      readVarInt(); // byte length
      int len = readVarInt();
      T[] array = (T[]) Array.newInstance(c, len);
      try {
         Method m = c.getMethod("from", String.class);
         for (int i = 0; i < len; i++) {
            String s = read_string(0);
            if (s != null) {
               array[i] = (T) m.invoke(null, s);
            }
         }
      } catch (Exception e) {
         throw new IOException(e);
      }
      return array;
   }

   @Override
   public <T extends Enum_int<T>> void write(int tag, T[] array) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      temp.writeVarInt(array.length);
      for (int i = 0; i < array.length; i++) {
         if (array[i] == null) {
            // enums having nulls in them is supported by using a value here not in the enum, we can't use a marker
            // byte as the structure adapter treats these as int arrays.  that means if we go service -> tetrapod -> client
            // the marker is exposed to clients instead of null TODO make specific type descriptor for enum_int
            temp.writeVarInt(-999);
         } else {
            temp.writeVarInt(array[i].getValue());
         }
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }

   @Override
   public <T extends Enum_String<T>> void write(int tag, T[] array) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      temp.writeVarInt(array.length);
      for (int i = 0; i < array.length; i++) {
         temp.writeStringNoTag(array[i] == null ? null : array[i].getValue());
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }

   @Override
   public void write_enum_list(int tag, List array) throws IOException {
      writeTag(tag, TYPE_LENGTH_DELIM);
      TempBufferDataSource temp = getTempBuffer();
      if (array == null) {
         array = Collections.EMPTY_LIST;
      }
      temp.writeVarInt(array.size());
      if (array.size() > 0) {
         Object e0 = array.get(0);
         for (Object o : array) {
            if (e0 instanceof Enum_int) {
               temp.writeVarInt(((Enum_int) o).getValue());
            } else if (e0 instanceof Enum_String) {
               temp.writeStringNoTag(((Enum_String) o).getValue());
            }
         }
      }
      writeVarInt(temp.rawCount());
      writeRawBytes(temp.rawBuffer(), 0, temp.rawCount());
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T extends Enum> List<T> read_enum_list(int tag, Class<T> c) throws IOException {
      readVarInt(); // byte length
      int len = readVarInt();
      List<T> array = new ArrayList<>();
      try {
         Method m;
         if (c.isAssignableFrom(Enum_int.class)) {
            m = c.getMethod("from", int.class);
            for (int i = 0; i < len; i++) {
               array.add((T) m.invoke(null, readVarInt()));
            }
         } else if (c.isAssignableFrom(Enum_int.class)) {
            m = c.getMethod("from", String.class);
            for (int i = 0; i < len; i++) {
               String s = read_string(0);
               if (s != null) {
                  array.add((T) m.invoke(null, s));
               }
            }
         } else {
            throw new IOException("Unknown enum type");
         }
      } catch (Exception e) {
         throw new IOException(e);
      }
      return array;
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

   protected void writeNullTag() throws IOException {
      writeTag(Codec.END_TAG, TYPE_NULL);
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
         int v = (int) (x & MASK);
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
      while (shift < 32) {
         int v = readRawByte();
         x = x | ((v & MASK) << shift);
         if ((v & CONTINUE) == 0)
            return x;
         shift += 7;
      }
      throw new IOException("Decoding failure");
   }

   protected long readVarLong() throws IOException {
      long x = 0;
      int shift = 0;
      while (shift < 64) {
         int v = readRawByte();
         x = x | (((long) (v & MASK)) << shift);
         if ((v & CONTINUE) == 0)
            return x;
         shift += 7;
      }
      throw new IOException("Decoding failure");
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
         tempBuffer = TempBufferDataSource.forWriting();
      tempBuffer.reset();
      return tempBuffer;
   }

   private boolean lastWasNull() {
      return lastTagType == TYPE_NULL;
   }

   @Override
   public Object getUnderlyingObject() {
      return null;
   }

}
