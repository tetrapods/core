package io.tetrapod.core.serialize.datasources;

import io.tetrapod.core.json.JSONObject;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.*;

import java.io.IOException;
import java.util.Iterator;

public class JSONDatasource implements DataSource {

   private JSONObject       json;
   private Iterator<String> keysIterator;

   public JSONDatasource() {
      this.json = new JSONObject();
   }
   
   public JSONDatasource(JSONObject json) {
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
         inst.read(new JSONDatasource(jo));
         return inst;
      } catch (InstantiationException | IllegalAccessException e) {
         throw new IOException("cannot instantiate class", e);
      }
   }

   @Override
   public <T extends Structure> void write(int tag, T struct) throws IOException {
      JSONDatasource jd = new JSONDatasource();
      struct.write(jd);
      json.put(Integer.toString(tag), jd.getJSON());
   }

}
