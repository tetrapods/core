package io.tetrapod.core.utils;

import java.util.*;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {
   private static final long  serialVersionUID = 1L;

   private static final float LOAD_FACTOR      = 0.75f;

   private final int          maxSize;

   public LRUCache(int maxSize) {
      super((int) Math.ceil(maxSize / LOAD_FACTOR) + 1, LOAD_FACTOR, true);
      this.maxSize = maxSize;
   }

   @Override
   protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
      return size() > maxSize;
   }
}
