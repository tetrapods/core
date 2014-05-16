package io.tetrapod.core;

import java.sql.*;

/**
 * Marshaler allows us to save different data types (the map values) to the SQLDB how we wish.
 */
public interface Marshaller<T> {
   /**
    * Marshals values as Strings
    */
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
   
      public String key(String memoryKey) {
         return memoryKey;
      }
   }

   /**
    * Marshals values as byte arrays / blobs
    */
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
   
      public String key(String memoryKey) {
         return memoryKey;
      }
   }

   public void add(T t, PreparedStatement s, int index) throws SQLException;

   public T get(ResultSet rs, int index) throws SQLException;

   public String getSQLValueType();
   
   /**
    * Note that key returned must be unique and stable for every input key
    */
   public String key(String memoryKey);
}