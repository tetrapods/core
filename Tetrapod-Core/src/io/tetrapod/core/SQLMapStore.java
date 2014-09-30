package io.tetrapod.core;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.dbcp.BasicDataSource;
import org.slf4j.*;

import com.hazelcast.core.MapStore;

/**
 * Handles persistence of a hazelcast distributed map to a MySQL database
 */
public class SQLMapStore<T> implements MapStore<String, T> {

   public static final Logger    logger = LoggerFactory.getLogger(SQLMapStore.class);

   private final BasicDataSource dataSource;
   private final String          tableName;
   private final Marshaller<T>   marshaller;

   public SQLMapStore(String tableName, Marshaller<T> marshaller) throws IOException {
      this.tableName = tableName;
      this.marshaller = marshaller;
      dataSource = new BasicDataSource();
      dataSource.setDriverClassName(System.getProperty("sql.driver"));
      dataSource.setUsername(System.getProperty("sql.user"));
      dataSource.setPassword(System.getProperty("sql.password"));
      dataSource.setUrl(System.getProperty("sql.jdbc"));
      dataSource.setDefaultAutoCommit(true);
      dataSource.setLogAbandoned(true);
      dataSource.setTestWhileIdle(true);
      dataSource.setTestOnBorrow(true);
      dataSource.setMaxActive(16);
      dataSource.setMaxWait(30000);
      dataSource.setValidationQuery("SELECT 1;");
      try (Connection con = dataSource.getConnection(); Statement s = con.createStatement()) {
         s.execute("CREATE TABLE IF NOT EXISTS " + tableName + " (id VARCHAR(255) PRIMARY KEY, val " + marshaller.getSQLValueType()
               + ") ENGINE=InnoDB;");
      } catch (SQLException e) {
         throw new IOException(e);
      }
   }

   public void shutdown() {
      try {
         dataSource.close();
      } catch (SQLException e) {
         logger.error(e.getMessage(), e);
      }
   }

   @Override
   public T load(String key) {
      final String query = "SELECT val FROM " + tableName + " WHERE id = ?";
      try (Connection con = dataSource.getConnection(); PreparedStatement s = con.prepareStatement(query)) {
         s.setString(1, marshaller.key(key));
         try (ResultSet rs = s.executeQuery()) {
            if (rs.next()) {
               return marshaller.get(rs, 1);
            } else {
               return null;
            }
         }
      } catch (SQLException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public Map<String, T> loadAll(Collection<String> keys) {
      return null; // lazy load from sql
   }

   @Override
   public Set<String> loadAllKeys() {
      return null; // lazy load from sql
   }

   @Override
   public void store(String key, T value) {
      logger.trace("store key {}", key);
      final String query = "INSERT INTO " + tableName + " (id, val) VALUES (?, ?) ON DUPLICATE KEY UPDATE val = ?";
      try (Connection con = dataSource.getConnection(); PreparedStatement s = con.prepareStatement(query)) {
         s.setString(1, marshaller.key(key));
         marshaller.add(value, s, 2);
         marshaller.add(value, s, 3);
         s.execute();
      } catch (SQLException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void storeAll(Map<String, T> map) {
      if (map.size() > 0) {
         logger.trace("store keys {}", map.keySet());
         final StringBuilder query = new StringBuilder();
         query.append("INSERT INTO " + tableName + " (id, val) VALUES");
         for (int i = 0; i < map.size(); i++) {
            query.append("\n\t(?, ?)");
            if (i < map.size() - 1) {
               query.append(", ");
            }
         }
         query.append("\n\tON DUPLICATE KEY UPDATE val=VALUES(val)");
         try (Connection con = dataSource.getConnection(); PreparedStatement s = con.prepareStatement(query.toString())) {
            int n = 1;
            for (Entry<String, T> entry : map.entrySet()) {
               s.setString(n++, marshaller.key(entry.getKey()));
               marshaller.add(entry.getValue(), s, n++);
            }
            s.execute();
         } catch (SQLException e) {
            throw new RuntimeException(query.toString(), e);
         }
      }
   }

   @Override
   public void delete(String key) {
      logger.trace("delete keys {}", key);
      final String query = "DELETE FROM " + tableName + " WHERE id = ?";
      try (Connection con = dataSource.getConnection(); PreparedStatement s = con.prepareStatement(query)) {
         s.setString(1, marshaller.key(key));
         s.execute();
      } catch (SQLException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void deleteAll(Collection<String> keys) {
      logger.trace("delete keys {}", keys);
      // OPTIMIZE: Could use bulk delete statement
      for (String key : keys) {
         delete(key);
      }
   }
}
