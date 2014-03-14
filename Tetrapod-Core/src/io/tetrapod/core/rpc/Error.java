package io.tetrapod.core.rpc;

import java.io.IOException;

import io.tetrapod.core.serialize.*;

public class Error extends Response {

   public int code;

   public Error(int code) {
      this.code = code;
   }

   public boolean isError() {
      return true;
   }

   @Override
   public void write(DataSource data) throws IOException {
      data.write(1, code);
   }

   @Override
   public void read(DataSource data) throws IOException {
      code = data.read_int(1);
   }

   @Override
   public int getStructId() {
      return 1;
   }
}
