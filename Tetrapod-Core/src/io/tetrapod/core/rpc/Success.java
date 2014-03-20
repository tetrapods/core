package io.tetrapod.core.rpc;

import java.io.IOException;

import io.tetrapod.core.serialize.*;

public class Success extends Response {

   @Override
   public void write(DataSource data) throws IOException {}

   @Override
   public void read(DataSource data) throws IOException {}

   @Override
   public int getStructId() {
      return 0;
   }
}
