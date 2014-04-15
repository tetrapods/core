package io.tetrapod.core;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.dbcp.BasicDataSource;
import org.slf4j.*;

import com.hazelcast.core.MapStore;

public class SQLMapStore implements MapStore<String, String> {

   public static final Logger    logger = LoggerFactory.getLogger(SQLMapStore.class);

   private final BasicDataSource dataSource;
   private final String          tableName;

   public SQLMapStore(String tableName) throws IOException {
      this.tableName = tableName;
      dataSource = new BasicDataSource();
      dataSource.setDriverClassName(System.getProperty("sql.driver"));
      dataSource.setUsername(System.getProperty("sql.user"));
      dataSource.setPassword(System.getProperty("sql.password"));
      dataSource.setUrl(System.getProperty("sql.jdbc"));
      dataSource.setDefaultAutoCommit(true);
      dataSource.setLogAbandoned(true);
      dataSource.setTestWhileIdle(true);
      try (Connection con = dataSource.getConnection(); Statement s = con.createStatement()) {
         s.execute("CREATE TABLE IF NOT EXISTS " + tableName + " (id VARCHAR(512) PRIMARY KEY, val MEDIUMTEXT) ENGINE=InnoDB;");
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
   public String load(String key) {
      final String query = "SELECT val FROM " + tableName + " WHERE id = ?";
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
      final String query = "INSERT INTO " + tableName + " (id, val) VALUES (?, ?) ON DUPLICATE KEY UPDATE val = ?";
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
      final String query = "DELETE FROM " + tableName + " WHERE id = ?";
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
