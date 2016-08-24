package io.tetrapod.core.rpc;

import io.tetrapod.core.utils.Util;

public class ContextIdGenerator {

   public static final ThreadLocal<Long> contextId = new ThreadLocal<>();

   public static long generate() {
      long ctxId = 0;
      while (ctxId == 0) {
         ctxId = Util.random.nextLong();
      }
      contextId.set(ctxId);
      return ctxId;
   }

   public static long getContextId() {
      Long ctxId = contextId.get();
      if (ctxId == null) {
         ctxId = generate();
      }
      return ctxId;
   }

   public static void setContextId(long ctxId) {
      contextId.set(ctxId);
   }

}
