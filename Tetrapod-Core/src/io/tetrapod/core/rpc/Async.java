package io.tetrapod.core.rpc;

import io.tetrapod.protocol.core.RequestHeader;

import org.slf4j.*;

public class Async {
   public static final Logger logger = LoggerFactory.getLogger(Async.class);

   // TODO: Timeouts

   public final RequestHeader header;
   public final Request       request;

   public Response            response;
   public ResponseHandler     handler;

   public Async(Request request, RequestHeader header) {
      this.request = request;
      this.header = header;
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
