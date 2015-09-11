package io.tetrapod.core.rpc;

import io.tetrapod.protocol.core.RequestHeader;

import org.slf4j.*;

abstract public class ResponseHandler {
   private static final Logger logger = LoggerFactory.getLogger(ResponseHandler.class);

   public static final ResponseHandler LOGGER = new ResponseHandler() {
      @Override
      public void onResponse(Response res) {
         if (res.isError()) {
            RequestHeader h = getRequestHeader();
            logger.error("[{}] {} failed with error = {}", h.requestId, h.dump(), res.errorCode());
         }
      }
   };

   private RequestHeader header;

   public RequestHeader getRequestHeader() {
      return header;
   }

   public final void fireResponse(Response res, RequestHeader header) {
      this.header = header;
      onResponse(res);
      this.header = null;
   }

   abstract public void onResponse(Response res);
}
