package io.tetrapod.core.kvstore;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.*;

import org.junit.Test;

public class KeyValueStoreTest {
   
   @Test
   public void basicTest() throws IOException {
      try {
         Files.delete(FileSystems.getDefault().getPath("misc.1"));
      } catch (NoSuchFileException e) {}
      KeyValueStore store = new KeyValueStore("misc", ".");
      store.put("test", "apple");
      store.put("test", "oranges");
      store.put("foo", 17);
      store.close();
      store = null;
      
      store = new KeyValueStore("misc", ".");
      int foo = (Integer)store.get("foo");
      assertEquals(17, foo);
      store.close();
   }

}
