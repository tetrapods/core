package io.tetrapod.core;

import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;

import java.util.*;
import java.util.concurrent.Callable;

import org.slf4j.*;

public class StructureFactory {

   protected static final Logger                logger       = LoggerFactory.getLogger(StructureFactory.class);

   private final Map<Long, Callable<Structure>> knownStructs = new HashMap<>();

   public synchronized void add(int contractId, int structId, Callable<Structure> factory) {
      long key = makeKey(contractId, structId);
      knownStructs.put(key, factory);
   }

   // OPTIMIZE: could make this class immutable using a builder pattern and avoid 
   //           this synchronize. adds are rare and usually upfront
   public synchronized Structure make(int serviceId, int structId) {
      if (structId == Success.STRUCT_ID) {
         return Response.SUCCESS;
      }
      if (structId == Error.STRUCT_ID) {
         return new Error();
      }
      long key = makeKey(serviceId, structId);
      Callable<Structure> c = knownStructs.get(key);
      if (c != null) {
         try {
            return c.call();
         } catch (Exception e) {
            logger.error(e.getMessage(), e);
         }
      }
      return null;
   }

   private final long makeKey(int serviceId, int structId) {
      return ((long) serviceId << 32) | (long) structId;
   }

}
