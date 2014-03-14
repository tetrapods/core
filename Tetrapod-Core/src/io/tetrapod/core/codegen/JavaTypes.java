package io.tetrapod.core.codegen;

import java.util.*;

class JavaTypes {
   
   public static class Info {
      String base;
      String boxed;
      boolean isPrimitive;
      String defaultValue;
      String defaultValueDelim = "";
      
      public Info(String base, String boxed, boolean isPrimitive, String defaultValue) {
         this.base = base;
         this.boxed = boxed;
         this.isPrimitive = isPrimitive;
         this.defaultValue = defaultValue;
      }
   }
   
   private static Map<String, Info> map = new HashMap<>();
   
   static {
      map.put("int", new Info("int", "Integer", true, "0"));
      map.put("long", new Info("long", "Long", true, "0"));
      map.put("double", new Info("double", "Double", true, "0"));
      map.put("byte", new Info("byte", "Byte", true, "0"));
      map.put("boolean", new Info("boolean", "Boolean", true, "false"));
      map.put("string", new Info("String", "String", true, "null"));
      map.get("string").defaultValueDelim = "\"";
   }
   
   public static Info get(String type) {
      Info i = map.get(type);
      if (i != null)
         return i;
      return new Info(type, type, false, "null");
   }

}
