package io.tetrapod.core.serialize;

import io.tetrapod.core.rpc.Enum_String;
import io.tetrapod.core.rpc.Flags_int;
import io.tetrapod.core.rpc.Flags_long;
import io.tetrapod.core.rpc.Enum_int;
import io.tetrapod.core.rpc.Structure;

import java.io.IOException;
import java.util.List;

/**
 * A fairly lengthy interface defining read/write interface for serializing code
 * generate objects.
 */
public interface DataSource {
   
   public int readTag() throws IOException;

   public String read_string(int tag) throws IOException;

   public int read_int(int tag) throws IOException;

   public byte read_byte(int tag) throws IOException;

   public long read_long(int tag) throws IOException;

   public double read_double(int tag) throws IOException;

   public boolean read_boolean(int tag) throws IOException;

   public <T extends Structure> T read_struct(int tag, T struct) throws IOException;

   public int[] read_int_array(int tag) throws IOException;

   public List<Integer> read_int_list(int tag) throws IOException;

   public void write(int tag, int[] array) throws IOException;

   public void write_int(int tag, List<Integer> list) throws IOException;

   public long[] read_long_array(int tag) throws IOException;

   public List<Long> read_long_list(int tag) throws IOException;

   public void write(int tag, long[] array) throws IOException;

   public void write_long(int tag, List<Long> list) throws IOException;

   public byte[] read_byte_array(int tag) throws IOException;

   public List<Byte> read_byte_list(int tag) throws IOException;

   public void write(int tag, byte[] array) throws IOException;

   public void write_byte(int tag, List<Byte> list) throws IOException;

   public boolean[] read_boolean_array(int tag) throws IOException;

   public List<Boolean> read_boolean_list(int tag) throws IOException;

   public void write(int tag, boolean[] array) throws IOException;

   public void write_boolean(int tag, List<Boolean> list) throws IOException;

   public double[] read_double_array(int tag) throws IOException;

   public List<Double> read_double_list(int tag) throws IOException;

   public void write(int tag, double[] array) throws IOException;

   public void write_double(int tag, List<Double> list) throws IOException;

   public String[] read_string_array(int tag) throws IOException;

   public List<String> read_string_list(int tag) throws IOException;

   public void write(int tag, String[] array) throws IOException;

   public void write_string(int tag, List<String> list) throws IOException;

   public <T extends Structure> T[] read_struct_array(int tag, T struct) throws IOException;

   public <T extends Structure> List<T> read_struct_list(int tag, T struct) throws IOException;

   public void write(int tag, int intval) throws IOException;

   public void write(int tag, byte byteval) throws IOException;

   public void write(int tag, double doubleval) throws IOException;

   public void write(int tag, long longval) throws IOException;

   public void write(int tag, boolean boolval) throws IOException;

   public void write(int tag, String stringval) throws IOException;

   public <T extends Structure> void write(int tag, T struct) throws IOException;

   public <T extends Structure> void write(int tag, T[] array) throws IOException;

   public <T extends Structure> void write_struct(int tag, List<T> list) throws IOException;
   
   public <T extends Flags_int<T>> T[] read_flags_int_array(int tag, T struct) throws IOException;

   public <T extends Flags_long<T>> T[] read_flags_long_array(int tag, T struct) throws IOException;

   public void write(int tag, Flags_int<?>[] array) throws IOException;
   
   public void write(int tag, Flags_long<?>[] array) throws IOException;

   public <T extends Enum_int<T>> T[] read_enum_int_array(int tag, Class<T> c) throws IOException;

   public <T extends Enum_String<T>> T[] read_enum_string_array(int tag, Class<T> c) throws IOException;

   public <T extends Enum_int<T>> void write(int tag, T[] array) throws IOException;

   public <T extends Enum_String<T>> void write(int tag, T[] array) throws IOException;

   public void write_enum_list(int tag, List array) throws IOException;

   public <T extends Enum> List<T> read_enum_list(int tag, Class<T> c) throws IOException;

   public void skip(int tag) throws IOException;

   public void writeEndTag() throws IOException;

   public Object getUnderlyingObject();

}
