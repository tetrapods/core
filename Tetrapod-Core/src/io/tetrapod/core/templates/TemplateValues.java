package io.tetrapod.core.templates;

import java.io.*;
import java.util.*;

public class TemplateValues {

   private static class Values {
      List<String> values = new ArrayList<>();
      String seperator = "";

      public boolean isEmpty() {
         return values.isEmpty();
      }
      
      public void write(Writer w, int indent) throws IOException {
         append(values.get(0), w, indent);
         for (int i = 1; i < values.size(); i++) {
            append(seperator, w, indent);
            append(values.get(i), w, indent);
         }
      }
      
      private void append(String s, Writer w, int indent) throws IOException {
         int N = s.length();
         for (int i = 0; i < N; i++) {
            char c = s.charAt(i);
            w.append(c);
            if (c == '\n') {
               for (int j = 0; j < indent; j++)
                  w.append(' ');
            }
         }
      }
   }
   
   private Map<String, Values> map = new HashMap<>();
   
   public TemplateValues(String ... strings) {
      for (int i = 0; i < strings.length; i += 2) {
         add(strings[i], strings[i+1]);
      }
   }
   
   public void add(String key, String value) {
      Values v = ensure(key);
      v.values.add(value);
   }

   public void set(String key, String value) {
      Values v = ensure(key);
      v.values.clear();
      v.values.add(value);
   }
   
   public void setSeperator(String key, String seperator) {
      Values v = ensure(key);
      v.seperator = seperator;
   }
   
   public boolean has(String key) {
      Values v = map.get(key);
      return v != null && ! v.isEmpty();
   }
   
   public void write(String key, Writer w, int indent) throws IOException {
      map.get(key).write(w, indent);
   }

   private Values ensure(String key) {
      Values v = map.get(key);
      if (v == null) {
         v = new Values();
         map.put(key, v);
      }
      return v;
   }

   public String get(String key) {
      return map.get(key).values.get(0);
   }

   public void setIfEmpty(String key, String value) {
      if (!has(key))
         set(key, value);
   }
   
}
