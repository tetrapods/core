package io.tetrapod.core.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.tetrapod.core.Contract;
import io.tetrapod.core.StructureFactory;
import io.tetrapod.protocol.core.RequestHeader;

abstract public class ResponseHandler {
   private static final Logger         logger = LoggerFactory.getLogger(ResponseHandler.class);

   public static final ResponseHandler LOGGER = new ResponseHandler() {
                                                 @Override
                                                 public void onResponse(Response res) {
                                                    if (res.isError()) {
                                                       logError(getRequestHeader(), res.errorCode());
                                                    }
                                                 }
                                              };

   public static ResponseHandler logErrorsExcept(int... errorsToIgnore) {
      return new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            if (res.isError()) {
               if (errorsToIgnore != null) {
                  for (int err : errorsToIgnore) {
                     if (err == res.errorCode())
                        return;
                  }
               }
               logError(getRequestHeader(), res.errorCode());
            }
         }
      };
   }

   private static void logError(RequestHeader h, int errCode) {
      logger.error("[{}] {} {}\nfailed with error = {}", h.requestId,
            h.dump(),
            StructureFactory.getName(h.contractId, h.structId),
            Contract.getErrorCode(errCode, h.contractId));
   }

   public static void logError(int contractId, int structId, int errCode) {
      logger.error("{} failed with error = {}",
            StructureFactory.getName(contractId, structId),
            Contract.getErrorCode(errCode, contractId));
   }

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
