package io.tetrapod.core;

import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.protocol.core.CoreContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubService implements ServiceAPI {
   private static final Logger logger = LoggerFactory.getLogger(SubService.class);
   private Contract contract;

   public SubService(Contract contract) {
      this.contract = contract;
   }

   public Contract getContract() {
      return contract;
   }

   public Response genericRequest(Request r, RequestContext ctx) {
      logger.error("unhandled request ( Context: {} ) {} from {}", String.format("%016x", ctx.header.contextId), r.dump(), ctx.header.dump());
      return new Error(CoreContract.ERROR_UNKNOWN_REQUEST);
   }

}
