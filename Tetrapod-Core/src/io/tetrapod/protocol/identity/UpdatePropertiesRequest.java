package io.tetrapod.protocol.identity;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class UpdatePropertiesRequest extends Request {

   public static final int STRUCT_ID = 1362696;
   
   public UpdatePropertiesRequest() {
      defaults();
   }

   public UpdatePropertiesRequest(int accountId, int properties, int mask, String authToken) {
      this.accountId = accountId;
      this.properties = properties;
      this.mask = mask;
      this.authToken = authToken;
   }   

   public int accountId;
   
   /**
    * new properties values
    */
   public int properties;
   
   /**
    * which properties to update
    */
   public int mask;
   public String authToken;

   public final Structure.Security getSecurity() {
      return Security.ADMIN;
   }

   public final void defaults() {
      accountId = 0;
      properties = 0;
      mask = 0;
      authToken = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.accountId);
      data.write(2, this.properties);
      data.write(3, this.mask);
      data.write(4, this.authToken);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.accountId = data.read_int(tag); break;
            case 2: this.properties = data.read_int(tag); break;
            case 3: this.mask = data.read_int(tag); break;
            case 4: this.authToken = data.read_string(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   @Override
   public final int getStructId() {
      return UpdatePropertiesRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestUpdateProperties(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestUpdateProperties(UpdatePropertiesRequest r, RequestContext ctx);
   }
   
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new UpdatePropertiesRequest(); }
      };
   }
   
   public final int getContractId() {
      return IdentityContract.CONTRACT_ID;
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[4+1];
      result[1] = "accountId";
      result[2] = "properties";
      result[3] = "mask";
      result[4] = "authToken";
      return result;
   }
   
}
