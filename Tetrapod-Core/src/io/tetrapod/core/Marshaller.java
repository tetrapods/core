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
      @Override
      public void add(String str, PreparedStatement s, int index) throws SQLException {
         s.setString(index, str);
      }
   
      @Override
      public String get(ResultSet rs, int index) throws SQLException {
         return rs.getString(index);
      }
   
      @Override
      public String getSQLValueType() {
         return "MEDIUMTEXT";
      }
   
      @Override
      public String key(String memoryKey) {
         return memoryKey;
      }
   }

   /**
    * Marshals values as byte arrays / blobs
    */
   public static class BytesMarshaller implements Marshaller<byte[]> {
      @Override
      public void add(byte[] data, PreparedStatement s, int index) throws SQLException {
         s.setBytes(index, data);
      }
   
      @Override
      public byte[] get(ResultSet rs, int index) throws SQLException {
         return rs.getBytes(index);
      }
   
      @Override
      public String getSQLValueType() {
         return "BLOB";
      }
   
      @Override
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