package io.tetrapod.core.utils;

import java.util.*;

public class PrefMap {

   private final Map<String, String> prefs = new HashMap<>();

   public String getPref(String key, String def) {
      return prefs.containsKey(key) ? prefs.get(key) : def;
   }

   public boolean getPref(String key, boolean def) {
      return prefs.containsKey(key) ? Boolean.parseBoolean(prefs.get(key)) : def;
   }

   public int getPref(String key, int def) {
      return prefs.containsKey(key) ? Integer.parseInt(prefs.get(key)) : def;
   }

   public long getPref(String key, long def) {
      return prefs.containsKey(key) ? Long.parseLong(prefs.get(key)) : def;
   }

   public void setPref(String key, String val) {
      prefs.put(key, val);
   }

   public void setPref(String key, boolean val) {
      prefs.put(key, Boolean.toString(val));
   }

   public void setPref(String key, int val) {
      prefs.put(key, Integer.toString(val));
   }

   public void setPref(String key, long val) {
      prefs.put(key, Long.toString(val));
   }

   public int size() {
      return prefs.size();
   }

   public Set<String> keys() {
      return prefs.keySet();
   }
}
