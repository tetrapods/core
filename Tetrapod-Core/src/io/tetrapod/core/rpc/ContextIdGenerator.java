package io.tetrapod.core.rpc;

import org.slf4j.*;

import io.tetrapod.core.tasks.TaskContext;
import io.tetrapod.core.utils.Util;

public class ContextIdGenerator {
   public static final Logger  logger     = LoggerFactory.getLogger(ContextIdGenerator.class);

   public static final String CONTEXT_ID = "contextId";
   private static final String CONTEXT_ID_RAW = "contextIdRaw";
   static {
      TaskContext.setMdcVariableName(CONTEXT_ID);
   }

   public static long generate() {
      long ctxId = 0;
      while (ctxId == 0) {
         ctxId = Util.random.nextLong();
      }
      TaskContext.set(CONTEXT_ID_RAW, ctxId);
      TaskContext.set(CONTEXT_ID, Long.toHexString(ctxId));
      return ctxId;
   }

   public static long getContextId() {
      if (!TaskContext.hasCurrent()) {
         logger.error("No task context set so we cannot get a context id", new Throwable());
         return 0;
      }
      Long ctxId = TaskContext.get(CONTEXT_ID_RAW);
      if (ctxId == null) {
         ctxId = generate();
      }
      return ctxId;
   }

   public static void setContextId(long ctxId) {
      if (!TaskContext.hasCurrent()) {
         logger.error("No task context set so we cannot set a context id", new Throwable());
         return;
      }

      TaskContext.set(CONTEXT_ID_RAW, ctxId);
      TaskContext.set(CONTEXT_ID, Long.toHexString(ctxId));
   }

   public static void clear() {
      if (!TaskContext.hasCurrent()) {
         logger.warn("No task context set so we cannot clear the context id");
         return;
      }

      TaskContext.clear(CONTEXT_ID);
      TaskContext.clear(CONTEXT_ID_RAW);
   }

}
