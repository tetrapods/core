package io.tetrapod.core.serialize.datasources;

import io.tetrapod.core.json.*;
import io.tetrapod.core.rpc.Enum_String;
import io.tetrapod.core.rpc.Enum_int;
import io.tetrapod.core.rpc.Flags_int;
import io.tetrapod.core.rpc.Flags_long;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.*;
import io.tetrapod.core.utils.Util;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;

/**
 * A datasource that supports reading and writing to json objects. The fields are keyed by the tag so it's not very readable. Using the
 * field names would be more readable but would require introducing a constraint that field names could not change.
 */
public class JSONDataSource implements DataSource {

   protected final JSONObject json;
   protected Iterator<String> keysIterator;

   public JSONDataSource() {
      this(new JSONObject());
   }

   public JSONDataSource(JSONObject json) {
      this.json = json;
   }

   public JSONDataSource(String str) {
      this(new JSONObject(str));
   }

   public JSONObject getJSON() {
      return json;
   }

   @Override
   public int readTag() throws IOException {
      if (keysIterator == null) {
         keysIterator = json.keys();
      }
      while (keysIterator.hasNext()) {
         String k = keysIterator.next().toString();
         if (Character.isDigit(k.charAt(0)))
            return Integer.parseInt(k);
      }
      return Codec.END_TAG;
   }

   @Override
   public String read_string(int tag) throws IOException {
      return json.optString(key(tag));
   }

   @Override
   public int read_int(int tag) throws IOException {
      return json.optInt(key(tag));
   }

   @Override
   public byte read_byte(int tag) throws IOException {
      return (byte) json.optInt(key(tag));
   }

   @Override
   public long read_long(int tag) throws IOException {
      return json.optLong(key(tag));
   }

   @Override
   public double read_double(int tag) throws IOException {
      return json.optDouble(key(tag));
   }

   @Override
   public boolean read_boolean(int tag) throws IOException {
      return json.optBoolean(key(tag));
   }

   @Override
   public void write(int tag, int intval) throws IOException {
      String k = key(tag);
      if (k != null)
         json.put(k, intval);
   }

   @Override
   public void write(int tag, byte byteval) throws IOException {
      String k = key(tag);
      if (k != null)
         json.put(k, byteval);
   }

   @Override
   public void write(int tag, double doubleval) throws IOException {
      String k = key(tag);
      if (k != null)
         json.put(k, doubleval);
   }

   @Override
   public void write(int tag, long longval) throws IOException {
      String k = key(tag);
      if (k != null)
         json.put(k, longval);
   }

   @Override
   public void write(int tag, boolean boolval) throws IOException {
      String k = key(tag);
      if (k != null)
         json.put(k, boolval);
   }

   @Override
   public void write(int tag, String stringval) throws IOException {
      if (stringval == null)
         stringval = "";
      String k = key(tag);
      if (k != null)
         json.put(k, stringval);
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
   public <T extends Structure> T read_struct(int tag, T struct) throws IOException {
      JSONObject jo = json.getJSONObject(key(tag));
      struct.read(getTemporarySource(jo, struct));
      return struct;
   }

   @Override
   public <T extends Structure> void write(int tag, T struct) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONDataSource jd = getTemporarySource(struct);
         struct.write(jd);
         json.put(k, jd.getJSON());
      }
   }

   @Override
   public int[] read_int_array(int tag) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      int[] res = new int[arr.length()];
      for (int i = 0; i < res.length; i++) {
         res[i] = arr.getInt(i);
      }
      return res;
   }

   @Override
   public List<Integer> read_int_list(int tag) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      List<Integer> res = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
         res.add(arr.getInt(i));
      }
      return res;
   }

   @Override
   public void write(int tag, int[] array) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr = new JSONArray(array);
         json.put(k, arr);
      }
   }

   @Override
   public void write_int(int tag, List<Integer> list) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr = new JSONArray(list);
         json.put(k, arr);
      }
   }

   @Override
   public long[] read_long_array(int tag) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      long[] res = new long[arr.length()];
      for (int i = 0; i < res.length; i++) {
         res[i] = arr.getLong(i);
      }
      return res;
   }

   @Override
   public List<Long> read_long_list(int tag) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      List<Long> res = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
         res.add(arr.getLong(i));
      }
      return res;

   }

   @Override
   public void write(int tag, long[] array) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr = new JSONArray(array);
         json.put(k, arr);
      }
   }

   @Override
   public void write_long(int tag, List<Long> list) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr = new JSONArray(list);
         json.put(k, arr);
      }
   }

   @Override
   public byte[] read_byte_array(int tag) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      byte[] res = new byte[arr.length()];
      for (int i = 0; i < res.length; i++) {
         res[i] = (byte) arr.getInt(i);
      }
      return res;
   }

   @Override
   public List<Byte> read_byte_list(int tag) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      List<Byte> res = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
         res.add((byte) arr.getInt(i));
      }
      return res;

   }

   @Override
   public void write(int tag, byte[] array) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr = new JSONArray(array);
         json.put(k, arr);
      }
   }

   @Override
   public void write_byte(int tag, List<Byte> list) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr = new JSONArray(list);
         json.put(k, arr);
      }
   }

   @Override
   public boolean[] read_boolean_array(int tag) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      boolean[] res = new boolean[arr.length()];
      for (int i = 0; i < res.length; i++) {
         res[i] = arr.getBoolean(i);
      }
      return res;
   }

   @Override
   public List<Boolean> read_boolean_list(int tag) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      List<Boolean> res = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
         res.add(arr.getBoolean(i));
      }
      return res;

   }

   @Override
   public void write(int tag, boolean[] array) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr = new JSONArray(array);
         json.put(k, arr);
      }
   }

   @Override
   public void write_boolean(int tag, List<Boolean> list) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr = new JSONArray(list);
         json.put(k, arr);
      }
   }

   @Override
   public double[] read_double_array(int tag) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      double[] res = new double[arr.length()];
      for (int i = 0; i < res.length; i++) {
         res[i] = arr.getDouble(i);
      }
      return res;
   }

   @Override
   public List<Double> read_double_list(int tag) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      List<Double> res = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
         res.add(arr.getDouble(i));
      }
      return res;
   }

   @Override
   public void write(int tag, double[] array) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr = new JSONArray(array);
         json.put(k, arr);
      }
   }

   @Override
   public void write_double(int tag, List<Double> list) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr = new JSONArray(list);
         json.put(k, arr);
      }
   }

   @Override
   public String[] read_string_array(int tag) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      String[] res = new String[arr.length()];
      for (int i = 0; i < res.length; i++) {
         res[i] = arr.getString(i);
      }
      return res;
   }

   @Override
   public List<String> read_string_list(int tag) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      List<String> res = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
         res.add(arr.getString(i));
      }
      return res;
   }

   @Override
   public void write(int tag, String[] array) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr = new JSONArray(array);
         for (int i = 0; i < array.length; i++) {
            if (array[i] == null) {
               arr.put(i, "");
            }
         }
         json.put(k, arr);
      }
   }

   @Override
   public void write_string(int tag, List<String> list) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr = new JSONArray(list);
         for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null) {
               arr.put(i, "");
            }
         }
         json.put(k, arr);
      }
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T extends Structure> T[] read_struct_array(int tag, T struct) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      T[] res = (T[]) Array.newInstance(struct.getClass(), arr.length());
      for (int i = 0; i < res.length; i++) {
         JSONObject jo = arr.getJSONObject(i);
         if (jo.has("__null__")) {
            res[i] = null;
         } else {
            T inst = i == 0 ? struct : (T) struct.make();
            inst.read(getTemporarySource(jo, inst));
            res[i] = inst;
         }
      }
      return res;
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T extends Structure> List<T> read_struct_list(int tag, T struct) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      List<T> res = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
         JSONObject jo = arr.getJSONObject(i);
         if (jo.has("__null__")) {
            res.add(null);
         } else {
            T inst = i == 0 ? struct : (T) struct.make();
            inst.read(getTemporarySource(jo, inst));
            res.add(inst);
         }
      }
      return res;
   }

   @Override
   public <T extends Structure> void write(int tag, T[] array) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr = new JSONArray();
         JSONObject myNull = new JSONObject("{\"__null__\": true}");
         for (T x : array) {
            if (x == null) {
               arr.put(myNull);
            } else {
               JSONDataSource jd = getTemporarySource(x);
               x.write(jd);
               arr.put(jd.getJSON());
            }
         }
         json.put(k, arr);
      }
   }

   @Override
   public <T extends Structure> void write_struct(int tag, List<T> list) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr = new JSONArray();
         JSONObject myNull = new JSONObject("{\"__null__\": true}");
         for (T x : list) {
            if (x == null) {
               arr.put(myNull);
            } else {
               JSONDataSource jd = getTemporarySource(x);
               x.write(jd);
               arr.put(jd.getJSON());
            }
         }
         json.put(k, arr);
      }
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T extends Flags_int<T>> T[] read_flags_int_array(int tag, T struct) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      T[] res = (T[]) Array.newInstance(struct.getClass(), arr.length());
      for (int i = 0; i < res.length; i++) {
         T inst = i == 0 ? struct : (T) struct.make();
         res[i] = inst.set(arr.getInt(i));
      }
      return res;
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T extends Flags_long<T>> T[] read_flags_long_array(int tag, T struct) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      T[] res = (T[]) Array.newInstance(struct.getClass(), arr.length());
      for (int i = 0; i < res.length; i++) {
         T inst = i == 0 ? struct : (T) struct.make();
         res[i] = inst.set(arr.getLong(i));
      }
      return res;
   }

   @Override
   public void write(int tag, Flags_int<?>[] array) throws IOException {
      String k = key(tag);
      if (k != null) {
         int[] intArray = new int[array.length];
         for (int i = 0; i < array.length; i++) {
            intArray[i] = array[i] == null ? 0 : array[i].value;
         }
         JSONArray arr = new JSONArray(intArray);
         json.put(k, arr);
      }
   }

   @Override
   public void write(int tag, Flags_long<?>[] array) throws IOException {
      String k = key(tag);
      if (k != null) {
         long[] longArray = new long[array.length];
         for (int i = 0; i < array.length; i++) {
            longArray[i] = array[i] == null ? 0 : array[i].value;
         }
         JSONArray arr = new JSONArray(longArray);
         json.put(k, arr);
      }
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T extends Enum_int<T>> T[] read_enum_int_array(int tag, Class<T> c) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      T[] res = (T[]) Array.newInstance(c, arr.length());
      try {
         Method m = c.getMethod("from", int.class);
         for (int i = 0; i < res.length; i++) {
            if (arr.get(i) instanceof Integer) {
               res[i] = (T) m.invoke(null, arr.getInt(i));
            }
         }
      } catch (Exception e) {
         throw new IOException(e);
      }
      return res;
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T extends Enum_String<T>> T[] read_enum_string_array(int tag, Class<T> c) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      T[] res = (T[]) Array.newInstance(c, arr.length());
      try {
         Method m = c.getMethod("from", String.class);
         for (int i = 0; i < res.length; i++) {
            if (arr.get(i) instanceof String) {
               res[i] = (T) m.invoke(null, arr.getString(i));
            }
         }
      } catch (Exception e) {
         throw new IOException(e);
      }
      return res;
   }

   @Override
   public <T extends Enum_int<T>> void write(int tag, T[] array) throws IOException {
      String k = key(tag);
      if (k != null) {
         Integer[] intArray = new Integer[array.length];
         for (int i = 0; i < array.length; i++) {
            if (array[i] != null) {
               intArray[i] = array[i].getValue();
            }
         }
         JSONArray arr = new JSONArray(intArray);
         json.put(k, arr);
      }
   }

   @Override
   public <T extends Enum_String<T>> void write(int tag, T[] array) throws IOException {
      String k = key(tag);
      if (k != null) {
         String[] strArray = new String[array.length];
         for (int i = 0; i < array.length; i++) {
            if (array[i] != null) {
               strArray[i] = array[i].getValue();
            }
         }
         JSONArray arr = new JSONArray(strArray);
         json.put(k, arr);
      }
   }

   @Override
   public void write_enum_list(int tag, List array) throws IOException {
      String k = key(tag);
      if (k != null) {
         JSONArray arr;
         if (!Util.isEmpty(array)) {
            Object e0 = array.get(0);

            if (e0 instanceof Enum_int) {
               ArrayList<Integer> vals = new ArrayList<>();
               for (Object o : array) {
                  vals.add(((Enum_int)o).getValue());
               }
               arr = new JSONArray(vals);
            } else if (e0 instanceof Enum_String) {
               ArrayList<String> vals = new ArrayList<>();
               for (Object o : array) {
                  vals.add(((Enum_String)o).getValue());
               }
               arr = new JSONArray(vals);
            } else {
               throw new IOException("Invalid enum type");
            }
         } else {
            arr = new JSONArray();
         }
         json.put(k, arr);
      }
   }

   @Override
   public <T extends Enum> List<T> read_enum_list(int tag, Class<T> c) throws IOException {
      JSONArray arr = getJSONArrayOrNull(json, key(tag));
      if (arr == null)
         return null;
      List<T> res = new ArrayList<>();
      try {
         Method m;
         if (c.isAssignableFrom(Enum_int.class)) {
            m = c.getMethod("from", int.class);
            for (int i = 0; i < arr.length(); i++) {
               if (arr.get(i) instanceof String) {
                  res.add(Util.cast(m.invoke(null, arr.getString(i))));
               }
            }
         } else if (c.isAssignableFrom(Enum_int.class)) {
            m = c.getMethod("from", String.class);
            for (int i = 0; i < arr.length(); i++) {
               if (arr.get(i) instanceof Integer) {
                  res.add(Util.cast(m.invoke(null, arr.getInt(i))));
               }
            }
         } else {
            throw new IOException("Unknown enum type");
         }
      } catch (Exception e) {
         throw new IOException(e);
      }
      return res;
   }

   protected String key(int tag) {
      return Integer.toString(tag);
   }

   protected JSONDataSource getTemporarySource(Structure inst) {
      return new JSONDataSource();
   }

   protected JSONDataSource getTemporarySource(JSONObject jo, Structure inst) {
      return new JSONDataSource(jo);
   }

   @Override
   public Object getUnderlyingObject() {
      return json;
   }

   private JSONArray getJSONArrayOrNull(JSONObject jo, String key) {
      if (jo.isNull(key))
         return null;
      return json.getJSONArray(key);
   }
}
