package io.tetrapod.core.serialize.datasources;

import io.tetrapod.core.json.JSONObject;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.Codec;

import java.io.IOException;
import java.util.*;

/**
 * Uses the webTagNames for json serialization instead of just the tag itself. Although a lot prettier this format is not recommended for
 * long term storage as people are free to rename their fields but are not free to rename the tags.
 */
public class WebJSONDataSource extends JSONDataSource {

   String[]             tagNames;
   Map<String, Integer> reverseMap = new HashMap<>();

   public WebJSONDataSource(String[] tagNames) {
      super();
      setTagName(tagNames);
   }

   public WebJSONDataSource(JSONObject jo, String[] tagNames) {
      super(jo);
      setTagName(tagNames);
   }

   @Override
   protected String key(int tag) {
      return tagNames[tag];
   }

   @Override
   public int readTag() throws IOException {
      if (keysIterator == null) {
         keysIterator = json.keys();
      }
      while (keysIterator.hasNext()) {
         String k = keysIterator.next().toString();
         Integer tag = reverseMap.get(k);
         if (tag != null)
            return tag;
      }
      return Codec.END_TAG;
   }

   private void setTagName(String[] names) {
      for (int i = 0; i < names.length; i++) {
         if (names[i] != null)
            reverseMap.put(names[i], i);
      }
      tagNames = names;
   }

   @Override
   protected JSONDataSource getTemporarySource(JSONObject jo, Structure inst) {
      return new WebJSONDataSource(jo, inst.tagWebNames());
   }

   @Override
   protected JSONDataSource getTemporarySource(Structure inst) {
      return new WebJSONDataSource(inst.tagWebNames());
   }

   public static String toJSONString(Structure struct) throws IOException {
      return toJSON(struct).toString();
   }

   public static JSONObject toJSON(Structure struct) throws IOException {
      final JSONDataSource ds = new WebJSONDataSource(struct.tagWebNames());
      struct.write(ds);
      return ds.getJSON();
   }
}
