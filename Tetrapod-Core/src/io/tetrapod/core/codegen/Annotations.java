package io.tetrapod.core.codegen;

import java.util.*;

class Annotations {
   
   private Map<String,List<String>> map = new HashMap<>();

   public List<String> get(String key) {
      return map.get(key);
   }

   public String getFirst(String key) {
      List<String> list = map.get(key);
      return list == null ? null : list.get(0);
   }
   
   public void add(String key, String value) {
      List<String> list = map.get(key);
      if (list == null) {
         list = new ArrayList<>();
         map.put(key, list);
      }
      list.add(value);
   }

}
