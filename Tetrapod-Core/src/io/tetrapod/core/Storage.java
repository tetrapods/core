package io.tetrapod.core;

import java.io.*;
import java.util.Properties;

import org.slf4j.*;

import com.hazelcast.config.*;
import com.hazelcast.core.*;

/**
 * Storage provides a persistent in-memory KV Store with support for basic key-value storage, sequential counters, and distributed locks.
 * 
 * It is intended for storing cluster configuration, counters, course grained locks, and is not suitable for big-data
 * 
 * This is currently being implemented on top of hazelcast, and persisted to MySQL
 */
public class Storage implements MembershipListener {
   public static final Logger         logger   = LoggerFactory.getLogger(Storage.class);

   private static final String        MAP_NAME = "tetrapod";

   private final Config               config;
   private final HazelcastInstance    hazelcast;
   private final IMap<String, String> map;

   private SQLMapStore                sqlStorage;

   public Storage() throws IOException {
      config = new XmlConfigBuilder(System.getProperty("hazelcast.configurationFile", "cfg/hazelcast.xml")).build();
      if (System.getProperty("sql.enabled", "false").equals("true")) {
         sqlStorage = new SQLMapStore();
         config.getMapConfig(MAP_NAME).setMapStoreConfig(new MapStoreConfig().setImplementation(sqlStorage).setWriteDelaySeconds(2));
      }
      hazelcast = Hazelcast.newHazelcastInstance(config);
      hazelcast.getCluster().addMembershipListener(this);
      map = hazelcast.getMap(MAP_NAME);

      loadDefaultProperties();
   }

   public void shutdown() {
      hazelcast.shutdown();
      sqlStorage.shutdown();
   }

   private void loadDefaultProperties() throws IOException {
      final Properties props = new Properties();
      try (Reader reader = new FileReader("cfg/default.properties")) {
         props.load(reader);
      }

      final int fileVersion = Integer.parseInt(props.getProperty("tetrapod.storage.defaults.version", "1").trim());
      final int storedVersion = Integer.parseInt(get("tetrapod.storage.defaults.version", "0").trim());

      if (storedVersion < fileVersion) {
         logger.info("INITIALIZING STORAGE from {} version {}", "cfg/default.properties", fileVersion);
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

   public String delete(String key) {
      return map.remove(key);
   }

   public String get(String key) {
      return map.get(key);
   }

   public String get(String key, String defaultVal) {
      String val = map.get(key);
      if (val == null) {
         val = defaultVal;
      }
      return val;
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
