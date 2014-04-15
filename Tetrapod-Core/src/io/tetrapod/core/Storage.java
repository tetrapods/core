package io.tetrapod.core;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.dbcp.BasicDataSource;
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
   private final BasicDataSource      dataSource;
   private final SQLMapStore          sqlStorage;

   public Storage() throws IOException {
      final boolean sqlEnabled = System.getProperty("sql.enabled", "false").equals("true");

      dataSource = new BasicDataSource();
      sqlStorage = new SQLMapStore();

      if (sqlEnabled) {
         dataSource.setDriverClassName(System.getProperty("sql.driver"));
         dataSource.setUsername(System.getProperty("sql.user"));
         dataSource.setPassword(System.getProperty("sql.password"));
         dataSource.setUrl(System.getProperty("sql.jdbc"));
         dataSource.setDefaultAutoCommit(true);
         dataSource.setLogAbandoned(true);
         dataSource.setTestWhileIdle(true);
         try (Connection con = dataSource.getConnection(); Statement s = con.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS kvtable (id VARCHAR(512) PRIMARY KEY, val MEDIUMTEXT) ENGINE=InnoDB;");
         } catch (SQLException e) {
            throw new IOException(e);
         }
      }

      config = new XmlConfigBuilder(System.getProperty("hazelcast.configurationFile", "cfg/hazelcast.xml")).build();
      if (sqlEnabled) {
         config.getMapConfig(MAP_NAME).setMapStoreConfig(new MapStoreConfig().setImplementation(sqlStorage).setWriteDelaySeconds(2));
      }
      hazelcast = Hazelcast.newHazelcastInstance(config);
      hazelcast.getCluster().addMembershipListener(this);
      map = hazelcast.getMap(MAP_NAME);

      loadDefaultProperties();
   }

   public void shutdown() {
      hazelcast.shutdown();
      try {
         dataSource.close();
      } catch (SQLException e) {
         logger.error(e.getMessage(), e);
      }
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

   private class SQLMapStore implements MapStore<String, String> {

      @Override
      public String load(String key) {
         final String query = "SELECT val FROM kvtable WHERE id = ?";
         try (Connection con = dataSource.getConnection(); PreparedStatement s = con.prepareStatement(query)) {
            s.setString(1, key);
            try (ResultSet rs = s.executeQuery()) {
               if (rs.next()) {
                  return rs.getString(1);
               } else {
                  return null;
               }
            }
         } catch (SQLException e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      public Map<String, String> loadAll(Collection<String> keys) {
         return null; // lazy load from sql
      }

      @Override
      public Set<String> loadAllKeys() {
         return null; // lazy load from sql
      }

      @Override
      public void store(String key, String value) {
         final String query = "INSERT INTO kvtable (id, val) VALUES (?, ?) ON DUPLICATE KEY UPDATE val = ?";
         try (Connection con = dataSource.getConnection(); PreparedStatement s = con.prepareStatement(query)) {
            s.setString(1, key);
            s.setString(2, value);
            s.setString(3, value);
            s.execute();
         } catch (SQLException e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      public void storeAll(Map<String, String> map) {
         if (map.size() > 0) {
            final StringBuilder query = new StringBuilder();
            query.append("INSERT INTO kvtable (id, val) VALUES");
            for (int i = 0; i < map.size(); i++) {
               query.append("\n\t(?, ?)");
               if (i < map.size() - 1) {
                  query.append(", ");
               }
            }
            query.append("\n\tON DUPLICATE KEY UPDATE val=VALUES(val)");
            try (Connection con = dataSource.getConnection(); PreparedStatement s = con.prepareStatement(query.toString())) {
               int n = 1;
               for (Entry<String, String> entry : map.entrySet()) {
                  s.setString(n++, entry.getKey());
                  s.setString(n++, entry.getValue());
               }
               s.execute();
            } catch (SQLException e) {
               throw new RuntimeException(query.toString(), e);
            }
         }
      }

      @Override
      public void delete(String key) {
         final String query = "DELETE FROM kvtable WHERE id = ?";
         try (Connection con = dataSource.getConnection(); PreparedStatement s = con.prepareStatement(query)) {
            s.setString(1, key);
            s.execute();
         } catch (SQLException e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      public void deleteAll(Collection<String> keys) {
         // OPTIMIZE: Could use bulk delete statement
         for (String key : keys) {
            delete(key);
         }
      }
   }

}
