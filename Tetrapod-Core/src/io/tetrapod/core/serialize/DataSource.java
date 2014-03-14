package io.tetrapod.core.serialize;

import io.tetrapod.core.rpc.Structure;

import java.io.IOException;

public interface DataSource {
   int readTag() throws IOException;

   int read_int(int tag) throws IOException;

   byte read_byte(int tag) throws IOException;

   long read_long(int tag) throws IOException;

   double read_double(int tag) throws IOException;

   boolean read_boolean(int tag) throws IOException;

   String read_string(int tag) throws IOException;

   <T extends Structure> T read_struct(int tag, Class<T> structClass) throws IOException;

   void write(int tag, int intval) throws IOException;

   void write(int tag, byte byteval) throws IOException;

   void write(int tag, double doubleval) throws IOException;

   void write(int tag, long longval) throws IOException;

   void write(int tag, boolean boolval) throws IOException;

   void write(int tag, String stringval) throws IOException;

   void writeEndTag() throws IOException;

   void skip(int tag) throws IOException;

   <T extends Structure> void write(int tag, T struct) throws IOException;

}
