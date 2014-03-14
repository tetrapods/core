package io.tetrapod.core.serialize;

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
