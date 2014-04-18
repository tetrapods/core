package io.tetrapod.core;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.dbcp.BasicDataSource;
import org.slf4j.*;

import com.hazelcast.core.MapStore;

public class SQLMapStore<T> implements MapStore<String, T> {

   public static final Logger    logger = LoggerFactory.getLogger(SQLMapStore.class);

   private final BasicDataSource dataSource;
   private final String          tableName;
   private final Marshaller<T>   marshaller;

   public interface Marshaller<T> {
      public void add(T t, PreparedStatement s, int index) throws SQLException;

      public T get(ResultSet rs, int index) throws SQLException;

      public String getSQLValueType();
   }

   public static class StringMarshaller implements Marshaller<String> {
      public void add(String str, PreparedStatement s, int index) throws SQLException {
         s.setString(index, str);
      }

      public String get(ResultSet rs, int index) throws SQLException {
         return rs.getString(index);
      }

      public String getSQLValueType() {
         return "MEDIUMTEXT";
      }
   }

   public static class BytesMarshaller implements Marshaller<byte[]> {
      public void add(byte[] data, PreparedStatement s, int index) throws SQLException {
         s.setBytes(index, data);
      }

      public byte[] get(ResultSet rs, int index) throws SQLException {
         return rs.getBytes(index);
      }

      public String getSQLValueType() {
         return "BLOB";
      }
   }

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
         s.setString(1, key);
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
      logger.debug("store key {}", key);
      final String query = "INSERT INTO " + tableName + " (id, val) VALUES (?, ?) ON DUPLICATE KEY UPDATE val = ?";
      try (Connection con = dataSource.getConnection(); PreparedStatement s = con.prepareStatement(query)) {
         s.setString(1, key);
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
         logger.debug("store keys {}", map.keySet());
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
               s.setString(n++, entry.getKey());
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
      logger.debug("delete keys {}", key);
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
      logger.debug("delete keys {}", keys);
      // OPTIMIZE: Could use bulk delete statement
      for (String key : keys) {
         delete(key);
      }
   }
}
