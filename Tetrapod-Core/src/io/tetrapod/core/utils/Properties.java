package io.tetrapod.core.utils;

import java.io.*;

public class Properties {

   final java.util.Properties props = new java.util.Properties();

   public void load(Class<?> context, String resourceName) throws IOException {
      try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getResourceAsStream(resourceName)))) {
         load(br);
      }
   }

   public void load(File filename) throws IOException {
      try (FileReader f = new FileReader(filename)) {
         load(f);
      }
   }

   public void load(Reader reader) throws IOException {
      props.load(reader);
   }

   public boolean optBoolean(String key, boolean defaultVal) {
      if (props.containsKey(key)) {
         return Boolean.parseBoolean(props.getProperty(key));
      }
      return defaultVal;
   }

   public int optInt(String key, int defaultVal) {
      if (props.containsKey(key)) {
         return Integer.parseInt(props.getProperty(key));
      }
      return defaultVal;
   }

   public String optString(String key, String defaultVal) {
      if (props.containsKey(key)) {
         return props.getProperty(key);
      }
      return defaultVal;
   }

}
