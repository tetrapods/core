package io.tetrapod.core.rpc;

import java.io.IOException;

import io.tetrapod.core.serialize.*;
import io.tetrapod.protocol.core.*;

public class Error extends Response {

   public static final int STRUCT_ID = 1;

   public int              code;

   public Error() {
      this(Core.ERROR_UNKNOWN);
   }

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
      return STRUCT_ID;
   }
   
   public int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }
   
   @Override
   public String[] tagWebNames() {
      return new String[] { "", "errorCode" };
   }
   
}
