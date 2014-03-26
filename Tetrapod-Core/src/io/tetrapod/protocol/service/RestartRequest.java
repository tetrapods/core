package io.tetrapod.protocol.service;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class RestartRequest extends Request {

   public static final int STRUCT_ID = 4802943;
   
   public RestartRequest() {
      defaults();
   }

   public RestartRequest(String restartNonce, boolean restartPaused) {
      this.restartNonce = restartNonce;
      this.restartPaused = restartPaused;
   }   

   public String restartNonce;
   public boolean restartPaused;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      restartNonce = null;
      restartPaused = false;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.restartNonce);
      data.write(2, this.restartPaused);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.restartNonce = data.read_string(tag); break;
            case 2: this.restartPaused = data.read_boolean(tag); break;
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
      return RestartRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is, RequestContext ctx) {
      if (is instanceof Handler)
         return ((Handler)is).requestRestart(this, ctx);
      return is.genericRequest(this, ctx);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestRestart(RestartRequest r, RequestContext ctx);
   }
   
   public final int getContractId() {
      return BaseServiceContract.CONTRACT_ID;
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "restartNonce";
      result[2] = "restartPaused";
      return result;
   }
   
   public final Structure make() {
      return new RestartRequest();
   }
}
