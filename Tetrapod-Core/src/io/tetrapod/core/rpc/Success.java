package io.tetrapod.core.rpc;

import java.io.IOException;

import io.tetrapod.core.serialize.*;

public class Success extends Response {

   public final int code;

   public Success(int code) {
      this.code = code;
   }

   public boolean isError() {
      return true;
   }

   @Override
   public void write(DataSource data) throws IOException {}

   @Override
   public void read(DataSource data) throws IOException {}

   @Override
   public int getStructId() {
      return 0;
   }
}
