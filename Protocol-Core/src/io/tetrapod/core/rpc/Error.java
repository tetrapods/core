package io.tetrapod.core.rpc;

import java.io.IOException;

import io.tetrapod.core.serialize.*;
import io.tetrapod.protocol.core.*;

public class Error extends Response {

   public static final int STRUCT_ID = 1;

   public int              code;

   public Error() {
      this(CoreContract.ERROR_UNKNOWN);
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
      data.writeEndTag();
   }

   @Override
   public void read(DataSource data) throws IOException {
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1:
               this.code = data.read_int(tag);
               break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }

   @Override
   public int getStructId() {
      return STRUCT_ID;
   }

   public int getContractId() {
      return CoreContract.CONTRACT_ID;
   }

   @Override
   public String[] tagWebNames() {
      return new String[] { "", "errorCode" };
   }

   @Override
   public String toString() {
      return "ERROR-" + code;
   }

}
