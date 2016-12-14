package io.tetrapod.core.gson;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.tetrapod.core.rpc.Enum_String;
import io.tetrapod.core.rpc.Enum_int;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * @author paulm
 *         Created: 9/14/16
 */
public class EnumStringAdapter <T extends Enum_String> implements JsonDeserializer<T>, JsonSerializer<T> {

   public static <E extends Enum_String> void register(GsonBuilder builder, E [] values) {
      builder.registerTypeAdapter(values[0].getClass(), new EnumStringAdapter<>(values));
   }


   private Map<String, T> map = new HashMap<>();

   public EnumStringAdapter(T[] values) {
      for (T value : values) {
         map.put(value.getValue(), value);
      }
   }

   @Override
   public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      if (json.isJsonNull()) {
         return null;
      }
      String val = json.getAsString();
      return map.get(val);
   }

   @Override
   public JsonElement serialize(T src, Type typeOfSrc, JsonSerializationContext context) {
      return new JsonPrimitive(src.getValue());
   }
}
