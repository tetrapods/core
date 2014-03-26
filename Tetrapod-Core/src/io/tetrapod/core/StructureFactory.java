package io.tetrapod.core;

import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;

import java.util.*;

import org.slf4j.*;

public class StructureFactory {

   protected static final Logger                logger       = LoggerFactory.getLogger(StructureFactory.class);

   private final Map<Long, Structure> knownStructs = new HashMap<>();

   public synchronized void add(Structure s) {
      long key = makeKey(s.getContractId(), s.getStructId());
      knownStructs.put(key, s);
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
      Structure c = knownStructs.get(key);
      if (c != null) {
         return c.make();
      }
      return null;
   }

   private final long makeKey(int serviceId, int structId) {
      return ((long) serviceId << 32) | (long) structId;
   }

}
