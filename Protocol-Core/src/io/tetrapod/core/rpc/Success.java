package io.tetrapod.core.rpc;

import io.tetrapod.core.serialize.DataSource;
import io.tetrapod.protocol.core.CoreContract;

import java.io.IOException;

public class Success extends Response {
   public static final int STRUCT_ID = 2;

   @Override
   public void write(DataSource data) throws IOException {}

   @Override
   public void read(DataSource data) throws IOException {}

   @Override
   public int getStructId() {
      return Success.STRUCT_ID;
   }
   
   public int getContractId() {
      return CoreContract.CONTRACT_ID;
   }
   
}
