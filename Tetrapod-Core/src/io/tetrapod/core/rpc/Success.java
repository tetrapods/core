package io.tetrapod.core.rpc;

import java.io.IOException;

import io.tetrapod.core.serialize.*;
import io.tetrapod.protocol.core.TetrapodContract;

public class Success extends Response {

   @Override
   public void write(DataSource data) throws IOException {}

   @Override
   public void read(DataSource data) throws IOException {}

   @Override
   public int getStructId() {
      return 0;
   }
   
   public int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }
}
