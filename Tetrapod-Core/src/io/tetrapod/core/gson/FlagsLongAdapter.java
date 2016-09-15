package io.tetrapod.core.gson;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.tetrapod.core.rpc.Flags_int;
import io.tetrapod.core.rpc.Flags_long;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.function.Function;

/**
 * @author paulm
 *         Created: 9/14/16
 */
public class FlagsLongAdapter<T extends Flags_long> implements JsonDeserializer<T>, JsonSerializer<T> {

   public static <E extends Flags_int> void register(GsonBuilder builder, Class<E> clz, Function<Integer, E> flagFactory) {
      builder.registerTypeAdapter(clz, flagFactory);
   }

   private Function<Long, T> flagFactory;

   public FlagsLongAdapter(Function<Long, T> flagFactory) {
      this.flagFactory = flagFactory;
   }

   @Override
   public T deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
      if (jsonElement.isJsonNull()) {
         return null;
      }
      long val = jsonElement.getAsLong();
      return flagFactory.apply(val);

   }

   @Override
   public JsonElement serialize(T t, Type type, JsonSerializationContext jsonSerializationContext) {
      return new JsonPrimitive(t.value);
   }


}
