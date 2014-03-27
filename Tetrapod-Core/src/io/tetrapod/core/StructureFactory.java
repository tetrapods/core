package io.tetrapod.core;

import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.*;

public class StructureFactory {

   protected static final Logger                logger       = LoggerFactory.getLogger(StructureFactory.class);

   private static final Map<Long, Structure> knownStructs = new ConcurrentHashMap<>();

   public static synchronized void add(Structure s) {
      long key = makeKey(s.getContractId(), s.getStructId());
      if (!knownStructs.containsKey(key))
         knownStructs.put(key, s);
   }

   public static synchronized Structure make(int serviceId, int structId) {
      if (structId == Success.STRUCT_ID) {
         return Response.SUCCESS;
      }
      if (structId == Error.STRUCT_ID) {
         return new Error();
      }
      long key = makeKey(serviceId, structId);
      Structure c = knownStructs.get(key);
      if (c != null) {
         return c.make();
      }
      return null;
   }

   private static long makeKey(int serviceId, int structId) {
      return ((long) serviceId << 32) | (long) structId;
   }

}
