package io.tetrapod.core.storage;

import io.tetrapod.core.*;
import io.tetrapod.core.serialize.HazelcastSerializer;
import io.tetrapod.core.utils.Util;

import java.io.*;

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
@Deprecated
public class HazelcastStorage extends Storage {
   public static final Logger         logger   = LoggerFactory.getLogger(HazelcastStorage.class);

   private static final String        MAP_NAME = "tetrapod";

   private final Config               config;
   private final HazelcastInstance    hazelcast;
   private final IMap<String, String> map;

   private SQLMapStore<String>        sqlStorage;

   public HazelcastStorage() throws IOException {
      String xml = HazelcastSerializer.hazelcastConfigFile(Util.getProperty("hazelcast.configurationFile", "cfg/hazelcast.xml"));
      config = new XmlConfigBuilder(new ByteArrayInputStream(xml.getBytes())).build();
      if (Util.getProperty("sql.enabled", false)) {
         sqlStorage = new SQLMapStore<>(MAP_NAME, new Marshaller.StringMarshaller());
         config.getMapConfig(MAP_NAME).setMapStoreConfig(new MapStoreConfig().setImplementation(sqlStorage).setWriteDelaySeconds(2));
      }
      hazelcast = Hazelcast.newHazelcastInstance(config);
      hazelcast.getCluster().addMembershipListener(new HazelcastSerializer.LoggingMembershipListener());
      map = hazelcast.getMap(MAP_NAME);

      loadDefaultProperties();
   }

   public void shutdown() {
      hazelcast.shutdown();
      if (sqlStorage != null) {
         sqlStorage.shutdown();
      }
   }

   public int getPort() {
      return hazelcast.getCluster().getLocalMember().getSocketAddress().getPort();
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
