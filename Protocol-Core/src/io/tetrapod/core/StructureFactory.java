package io.tetrapod.core;

import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StructureFactory {

   private static final Map<Long, Structure> knownStructs = new ConcurrentHashMap<>();

   public static synchronized void add(Structure s) {
      long key = makeKey(s.getContractId(), s.getStructId());
      // newest wins
      knownStructs.put(key, s);
   }

   public static synchronized Structure make(int contractId, int structId) {
      if (structId == Success.STRUCT_ID) {
         return Response.SUCCESS;
      }
      if (structId == Error.STRUCT_ID) {
         return new Error();
      }
      long key = makeKey(contractId, structId);
      Structure c = knownStructs.get(key);
      if (c != null) {
         return c.make();
      }
      return null;
   }

   private static long makeKey(int contractId, int structId) {
      return ((long) contractId << 32) | (long) structId;
   }

   public static synchronized String getName(int contractId, int structId) {
      long key = makeKey(contractId, structId);
      Structure c = knownStructs.get(key);
      if (c != null) {
         return c.getClass().getSimpleName();
      }
      return String.format("<%d,%d>", contractId, structId);
   }

}
