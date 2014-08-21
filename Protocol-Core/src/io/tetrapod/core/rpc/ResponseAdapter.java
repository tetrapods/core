package io.tetrapod.core.rpc;

import io.tetrapod.core.serialize.DataSource;

import java.io.IOException;

/**
 * Wraps an unknown structure as a Response type
 */
public class ResponseAdapter extends Response {

   private final Structure struct;

   public ResponseAdapter(Structure struct) {
      this.struct = struct;
   }

   @Override
   public void write(DataSource data) throws IOException {
      struct.write(data);
   }

   @Override
   public void read(DataSource data) throws IOException {
      struct.read(data);
   }

   @Override
   public int getStructId() {
      return struct.getStructId();
   }

   @Override
   public int getContractId() {
      return struct.getContractId();
   }

   @Override
   public String[] tagWebNames() {
      return struct.tagWebNames();
   }
}
