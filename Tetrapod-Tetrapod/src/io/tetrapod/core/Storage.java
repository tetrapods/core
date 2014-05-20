package io.tetrapod.core;

import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.HazelcastSerializer;
import io.tetrapod.core.serialize.datasources.JSONDataSource;
import io.tetrapod.core.utils.Util;

import java.io.*;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.slf4j.*;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.hazelcast.util.Base64;

/**
 * Storage provides a persistent in-memory KV Store with support for basic key-value storage, sequential counters, and distributed locks.
 * 
 * It is intended for storing cluster configuration, counters, course grained locks, and is not suitable for big-data
 * 
 * This is currently being implemented on top of hazelcast, and persisted to MySQL
 */
public class Storage implements MembershipListener {
   public static final Logger         logger            = LoggerFactory.getLogger(Storage.class);

   private static final String        MAP_NAME          = "tetrapod";
   private static final String        SHARED_SECRET_KEY = "tetrapod.shared.secret";

   private final Config               config;
   private final HazelcastInstance    hazelcast;
   private final IMap<String, String> map;

   private SQLMapStore<String>        sqlStorage;

   public Storage() throws IOException {
      String xml = HazelcastSerializer.hazelcastConfigFile(Util.getProperty("hazelcast.configurationFile", "cfg/hazelcast.xml"));
      config = new XmlConfigBuilder(new ByteArrayInputStream(xml.getBytes())).build();
      if (Util.getProperty("sql.enabled", false)) {
         sqlStorage = new SQLMapStore<>(MAP_NAME, new Marshaller.StringMarshaller());
         config.getMapConfig(MAP_NAME).setMapStoreConfig(new MapStoreConfig().setImplementation(sqlStorage).setWriteDelaySeconds(2));
      }
      hazelcast = Hazelcast.newHazelcastInstance(config);
      hazelcast.getCluster().addMembershipListener(this);
      map = hazelcast.getMap(MAP_NAME);

      loadDefaultProperties();
   }

   public byte[] getSharedSecret() {
      String str = map.get(SHARED_SECRET_KEY);
      if (str != null) {
         return Base64.decode(str.getBytes(Charset.forName("UTF-8")));
      } else {
         byte[] b = new byte[64];
         Random r = new SecureRandom();
         r.nextBytes(b);
         map.put(SHARED_SECRET_KEY, new String(Base64.encode(b), Charset.forName("UTF-8")));
         return b;
      }
   }

   public void shutdown() {
      hazelcast.shutdown();
      if (sqlStorage != null) {
         sqlStorage.shutdown();
      }
   }

   private void loadDefaultProperties() throws IOException {
      final Properties props = new Properties();
      try (Reader reader = new FileReader("cfg/storage.properties")) {
         props.load(reader);
      }

      final int fileVersion = Integer.parseInt(props.getProperty("tetrapod.storage.defaults.version", "1").trim());
      final int storedVersion = Integer.parseInt(get("tetrapod.storage.defaults.version", "0").trim());

      if (storedVersion < fileVersion) {
         logger.info("INITIALIZING STORAGE from {} version {}", "cfg/storage.properties", fileVersion);
         for (Object key : props.keySet()) {
            map.put(key.toString(), props.get(key).toString());
         }
      }
   }

   public int getPort() {
      return hazelcast.getCluster().getLocalMember().getSocketAddress().getPort();
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////

   public void memberAdded(MembershipEvent membersipEvent) {
      logger.info("Hazelcast Member Added: " + membersipEvent);
   }

   public void memberRemoved(MembershipEvent membersipEvent) {
      logger.info("Hazelcast Member Removed: " + membersipEvent);
   }

   @Override
   public void memberAttributeChanged(MemberAttributeEvent membersipEvent) {
      logger.info("Hazelcast Attribute Changed: " + membersipEvent);
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////

   public void put(String key, String value) {
      map.put(key, value);
   }

   public void put(String key, String value, int ttl, TimeUnit unit) {
      map.put(key, value, ttl, unit);
   }

   public void put(String key, int value) {
      map.put(key, Integer.toString(value));
   }

   public void put(String key, long value) {
      map.put(key, Long.toString(value));
   }

   public String delete(String key) {
      return map.remove(key);
   }

   public String get(String key) {
      return map.get(key);
   }

   public <T extends Structure> T read(String key, T struct) throws IOException {
      final String val = map.get(key);
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
      map.put(key, data.getJSON().toString());
   }

   public String get(String key, Object defaultVal) {
      String val = map.get(key);
      if (val == null) {
         val = defaultVal.toString();
      }
      return val;
   }

   public int get(String key, int defaultVal) {
      String val = map.get(key);
      if (val == null) {
         return defaultVal;
      }
      return Integer.parseInt(val);
   }

   public long get(String key, long defaultVal) {
      String val = map.get(key);
      if (val == null) {
         return defaultVal;
      }
      return Long.parseLong(val);
   }

   public ILock getLock(String lockKey) {
      return hazelcast.getLock(lockKey);
   }

   public long increment(String key) {
      getLock(key).lock();
      try {
         long val = 1 + get(key, 0L);
         put(key, val);
         return val;
      } finally {
         getLock(key).unlock();
      }
   }
 

}
