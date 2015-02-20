package io.tetrapod.core.storage;

import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.datasources.JSONDataSource;

import java.io.*;
import java.util.Properties;

import org.slf4j.*;

import com.hazelcast.core.ILock;

/**
 * Storage provides a persistent in-memory KV Store with support for basic key-value storage, sequential counters, and distributed locks.
 * 
 * It is intended for storing cluster configuration, counters, course grained locks, and is not suitable for big-data
 */
public abstract class Storage {
   public static final Logger logger = LoggerFactory.getLogger(Storage.class);

   @Deprecated
   protected void loadDefaultProperties() throws IOException {
      final Properties props = new Properties();
      try (Reader reader = new FileReader("cfg/storage.properties")) {
         props.load(reader);
      }

      final int fileVersion = Integer.parseInt(props.getProperty("tetrapod.storage.defaults.version", "1").trim());
      final int storedVersion = Integer.parseInt(get("tetrapod.storage.defaults.version", "0").trim());

      if (storedVersion < fileVersion) {
         logger.info("INITIALIZING STORAGE from {} version {}", "cfg/storage.properties", fileVersion);
         for (Object key : props.keySet()) {
            put(key.toString(), props.get(key).toString());
         }
      }
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////

   public abstract void shutdown();

   public abstract void put(String key, String value);

   public abstract String delete(String key);

   public abstract String get(String key);

   public abstract ILock getLock(String lockKey);

   public abstract long increment(String key);

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////

   public <T extends Structure> T read(String key, T struct) throws IOException {
      final String val = get(key);
      if (val != null) {
         JSONDataSource data = new JSONDataSource(val);
         struct.read(data);
         return struct;
      } else {
         return null;
      }
   }

   public void put(String key, Structure value) throws IOException {
      JSONDataSource data = new JSONDataSource();
      value.write(data);
      put(key, data.getJSON().toString());
   }

   public void put(String key, int value) {
      put(key, Integer.toString(value));
   }

   public void put(String key, long value) {
      put(key, Long.toString(value));
   }

   public String get(String key, Object defaultVal) {
      String val = get(key);
      if (val == null) {
         val = defaultVal.toString();
      }
      return val;
   }

   public int get(String key, int defaultVal) {
      String val = get(key);
      if (val == null) {
         return defaultVal;
      }
      return Integer.parseInt(val);
   }

   public long get(String key, long defaultVal) {
      String val = get(key);
      if (val == null) {
         return defaultVal;
      }
      return Long.parseLong(val);
   }

}
