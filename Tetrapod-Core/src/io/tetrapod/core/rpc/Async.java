package io.tetrapod.core.rpc;

import org.slf4j.*;

public class Async {
   public static final Logger logger = LoggerFactory.getLogger(Async.class);

   public final int           requestId;
   public final Request       request;

   public Response            response;
   public ResponseHandler     handler;

   public Async(Request request, int requestId) {
      this.request = request;
      this.requestId = requestId;
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
         try {
            handler.onResponse(res, errorCode);
         } catch (Throwable e) {
            logger.error(e.getMessage(), e);
         }
      }
   }

}
