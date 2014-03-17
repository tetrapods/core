package io.tetrapod.core.serialize.datasources;

import io.tetrapod.core.json.*;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.*;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

public class JSONDataSource implements DataSource {

   private JSONObject       json;
   private Iterator<String> keysIterator;

   public JSONDataSource() {
      this.json = new JSONObject();
   }
   
   public JSONDataSource(JSONObject json) {
      this.json = json;
   }
   
   public JSONObject getJSON() {
      return json;
   }
   
   @Override
   public int readTag() throws IOException {
      if (keysIterator == null) {
         keysIterator = json.keys();
      }
      if (keysIterator.hasNext())
         return Integer.parseInt(keysIterator.next().toString());
      return Codec.END_TAG;
   }

   @Override
   public String read_string(int tag) throws IOException {
      return json.optString(Integer.toString(tag));
   }

   @Override
   public int read_int(int tag) throws IOException {
      return json.optInt(Integer.toString(tag));
   }

   @Override
   public byte read_byte(int tag) throws IOException {
      return (byte)json.optInt(Integer.toString(tag));
   }

   @Override
   public long read_long(int tag) throws IOException {
      return json.optLong(Integer.toString(tag));
   }

   @Override
   public double read_double(int tag) throws IOException {
      return json.optDouble(Integer.toString(tag));
   }

   @Override
   public boolean read_boolean(int tag) throws IOException {
      return json.optBoolean(Integer.toString(tag));
   }

   @Override
   public void write(int tag, int intval) throws IOException {
      json.put(Integer.toString(tag), intval);
   }

   @Override
   public void write(int tag, byte byteval) throws IOException {
      json.put(Integer.toString(tag), byteval);
   }

   @Override
   public void write(int tag, double doubleval) throws IOException {
      json.put(Integer.toString(tag), doubleval);
   }

   @Override
   public void write(int tag, long longval) throws IOException {
      json.put(Integer.toString(tag), longval);
   }

   @Override
   public void write(int tag, boolean boolval) throws IOException {
      json.put(Integer.toString(tag), boolval);
   }

   @Override
   public void write(int tag, String stringval) throws IOException {
      if (stringval == null)
         stringval = "";
      json.put(Integer.toString(tag), stringval);
   }

   @Override
   public void skip(int tag) throws IOException {
      // no op
   }

   @Override
   public void writeEndTag() throws IOException {
      // no op
   }

   @Override
   public <T extends Structure> T read_struct(int tag, Class<T> structClass) throws IOException {
      try {
         JSONObject jo = json.getJSONObject(Integer.toString(tag));
         T inst = structClass.newInstance();
         inst.read(new JSONDataSource(jo));
         return inst;
      } catch (InstantiationException | IllegalAccessException e) {
         throw new IOException("cannot instantiate class", e);
      }
   }

   @Override
   public <T extends Structure> void write(int tag, T struct) throws IOException {
      JSONDataSource jd = new JSONDataSource();
      struct.write(jd);
      json.put(Integer.toString(tag), jd.getJSON());
   }

   @Override
   public int[] read_int_array(int tag) throws IOException {
      JSONArray arr = json.getJSONArray(Integer.toString(tag));
      int[] res = new int[arr.length()];
      for (int i = 0; i < res.length; i++) {
         res[i] = arr.getInt(i);
      }
      return res;
   }

   @Override
   public List<Integer> read_int_list(int tag) throws IOException {
      JSONArray arr = json.getJSONArray(Integer.toString(tag));
      List<Integer> res = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
         res.add(arr.getInt(i));
      }
      return res;
   }

   @Override
   public void write(int tag, int[] array) throws IOException {
      JSONArray arr = new JSONArray(array);
      json.put(Integer.toString(tag), arr);
   }

   @Override
   public void write_int(int tag, List<Integer> list) throws IOException {
      JSONArray arr = new JSONArray(list);
      json.put(Integer.toString(tag), arr);
   }

   @Override
   public long[] read_long_array(int tag) throws IOException {
      JSONArray arr = json.getJSONArray(Integer.toString(tag));
      long[] res = new long[arr.length()];
      for (int i = 0; i < res.length; i++) {
         res[i] = arr.getLong(i);
      }
      return res;
   }

   @Override
   public List<Long> read_long_list(int tag) throws IOException {
      JSONArray arr = json.getJSONArray(Integer.toString(tag));
      List<Long> res = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
         res.add(arr.getLong(i));
      }
      return res;

   }

   @Override
   public void write(int tag, long[] array) throws IOException {
      JSONArray arr = new JSONArray(array);
      json.put(Integer.toString(tag), arr);
   }

   @Override
   public void write_long(int tag, List<Long> list) throws IOException {
      JSONArray arr = new JSONArray(list);
      json.put(Integer.toString(tag), arr);
   }

   @Override
   public byte[] read_byte_array(int tag) throws IOException {
      JSONArray arr = json.getJSONArray(Integer.toString(tag));
      byte[] res = new byte[arr.length()];
      for (int i = 0; i < res.length; i++) {
         res[i] = (byte)arr.getInt(i);
      }
      return res;
   }

   @Override
   public List<Byte> read_byte_list(int tag) throws IOException {
      JSONArray arr = json.getJSONArray(Integer.toString(tag));
      List<Byte> res = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
         res.add((byte)arr.getInt(i));
      }
      return res;

   }

   @Override
   public void write(int tag, byte[] array) throws IOException {
      JSONArray arr = new JSONArray(array);
      json.put(Integer.toString(tag), arr);
   }

   @Override
   public void write_byte(int tag, List<Byte> list) throws IOException {
      JSONArray arr = new JSONArray(list);
      json.put(Integer.toString(tag), arr);
   }

   @Override
   public boolean[] read_boolean_array(int tag) throws IOException {
      JSONArray arr = json.getJSONArray(Integer.toString(tag));
      boolean[] res = new boolean[arr.length()];
      for (int i = 0; i < res.length; i++) {
         res[i] = arr.getBoolean(i);
      }
      return res;
   }

   @Override
   public List<Boolean> read_boolean_list(int tag) throws IOException {
      JSONArray arr = json.getJSONArray(Integer.toString(tag));
      List<Boolean> res = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
         res.add(arr.getBoolean(i));
      }
      return res;

   }

   @Override
   public void write(int tag, boolean[] array) throws IOException {
      JSONArray arr = new JSONArray(array);
      json.put(Integer.toString(tag), arr);
   }

   @Override
   public void write_boolean(int tag, List<Boolean> list) throws IOException {
      JSONArray arr = new JSONArray(list);
      json.put(Integer.toString(tag), arr);
   }

   @Override
   public double[] read_double_array(int tag) throws IOException {
      JSONArray arr = json.getJSONArray(Integer.toString(tag));
      double[] res = new double[arr.length()];
      for (int i = 0; i < res.length; i++) {
         res[i] = arr.getDouble(i);
      }
      return res;
   }

   @Override
   public List<Double> read_double_list(int tag) throws IOException {
      JSONArray arr = json.getJSONArray(Integer.toString(tag));
      List<Double> res = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
         res.add(arr.getDouble(i));
      }
      return res;
   }

   @Override
   public void write(int tag, double[] array) throws IOException {
      JSONArray arr = new JSONArray(array);
      json.put(Integer.toString(tag), arr);
   }

   @Override
   public void write_double(int tag, List<Double> list) throws IOException {
      JSONArray arr = new JSONArray(list);
      json.put(Integer.toString(tag), arr);
   }

   @Override
   public String[] read_string_array(int tag) throws IOException {
      JSONArray arr = json.getJSONArray(Integer.toString(tag));
      String[] res = new String[arr.length()];
      for (int i = 0; i < res.length; i++) {
         res[i] = arr.getString(i);
      }
      return res;
   }

   @Override
   public List<String> read_string_list(int tag) throws IOException {
      JSONArray arr = json.getJSONArray(Integer.toString(tag));
      List<String> res = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
         res.add(arr.getString(i));
      }
      return res;
   }

   @Override
   public void write(int tag, String[] array) throws IOException {
      JSONArray arr = new JSONArray(array);
      for (int i = 0; i < array.length; i++) {
         if (array[i] == null) {
            arr.put(i, "");
         }
      }
      json.put(Integer.toString(tag), arr);
   }

   @Override
   public void write_string(int tag, List<String> list) throws IOException {
      JSONArray arr = new JSONArray(list);
      for (int i = 0; i < list.size(); i++) {
         if (list.get(i) == null) {
            arr.put(i, "");
         }
      }
      json.put(Integer.toString(tag), arr);
   }

   @Override
   public <T extends Structure> T[] read_struct_array(int tag, Class<T> structClass) throws IOException {
      try {
         JSONArray arr = json.getJSONArray(Integer.toString(tag));
         @SuppressWarnings("unchecked")
         T[] res = (T[])Array.newInstance(structClass, arr.length());
         for (int i = 0; i < res.length; i++) {
            JSONObject jo = json.getJSONObject(Integer.toString(tag));
            T inst = structClass.newInstance();
            inst.read(new JSONDataSource(jo));
            res[i] = inst;
         }
         return res;
      } catch (InstantiationException | IllegalAccessException e) {
         // TODO LOG
      }
      return null;
   }

   @Override
   public <T extends Structure> List<T> read_struct_list(int tag, Class<T> structClass) throws IOException {
      try {
         JSONArray arr = json.getJSONArray(Integer.toString(tag));
         List<T> res = new ArrayList<>();
         for (int i = 0; i < arr.length(); i++) {
            JSONObject jo = json.getJSONObject(Integer.toString(tag));
            T inst = structClass.newInstance();
            inst.read(new JSONDataSource(jo));
            res.add(inst);
         }
         return res;
      } catch (InstantiationException | IllegalAccessException e) {
         // TODO LOG
      }
      return null;   
   }

   @Override
   public <T extends Structure> void write(int tag, T[] array) throws IOException {
      JSONArray arr = new JSONArray();
      for (T x : array) {
         JSONDataSource jd = new JSONDataSource();
         x.write(jd);
         arr.put(jd.getJSON());
      }
      json.put(Integer.toString(tag), arr);
   }

   @Override
   public <T extends Structure> void write_struct(int tag, List<T> list) throws IOException {
      JSONArray arr = new JSONArray();
      for (T x : list) {
         JSONDataSource jd = new JSONDataSource();
         x.write(jd);
         arr.put(jd.getJSON());
      }
      json.put(Integer.toString(tag), arr);
   }

}
