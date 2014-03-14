package io.tetrapod.core.rpc;

import io.tetrapod.core.serialize.*;

import org.slf4j.*;

public class Async {
   public static final Logger logger = LoggerFactory.getLogger(Async.class);

   public final Request       request;

   public Response            response;
   public ResponseHandler     handler;

   public Async(Request request) {
      this.request = request;
   }

   public static interface ResponseHandler {
      public void onResponse(Response res, int errorCode);
   }

   public synchronized void handle(ResponseHandler handler) {
      this.handler = handler;
      if (response != null) {
         handler.onResponse(response, 0);
      }
   }

   public synchronized void setResponse(Response res, int errorCode) {
      response = res;
      if (handler != null) {
         handler.onResponse(res, errorCode);
      }
   }

}
