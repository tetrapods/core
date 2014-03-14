package io.tetrapod.core.rpc;

import io.tetrapod.core.serialize.DataSource;

import java.io.IOException;

abstract public class Structure {

   abstract public void write(DataSource data) throws IOException;

   abstract public void read(DataSource data) throws IOException;

   abstract public int getStructId();

   @Override
   public String toString() {
      return getClass().getSimpleName();
   }
}
