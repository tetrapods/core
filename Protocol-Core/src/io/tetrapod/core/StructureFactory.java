package io.tetrapod.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.protocol.core.*;

public class StructureFactory {

   private static final Map<Long, Structure> knownStructs = new ConcurrentHashMap<>();

   public static synchronized void add(Structure s) {
      final long key = makeKey(s.getContractId(), s.getStructId());
      // newest wins
      knownStructs.put(key, s);
   }

   public static synchronized void addIfNew(Structure s) {
      final long key = makeKey(s.getContractId(), s.getStructId());
      if (!knownStructs.containsKey(key)) {
         knownStructs.put(key, s);
      }
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

   public static boolean has(int contractId, int structId) {
      return knownStructs.containsKey(makeKey(contractId, structId));
   }

   public static StructDescription getDescription(int contractId, int structId) {
      Structure s = knownStructs.get(makeKey(contractId, structId));
      if (s != null) {
         return s.makeDescription();
      }
      return null;
   }

   private static long makeKey(int contractId, int structId) {
      return ((long) contractId << 32) | (long) structId;
   }

   public static synchronized String getName(int contractId, int structId) {
      if (contractId == CoreContract.CONTRACT_ID) {
         switch (structId) {
            case Success.STRUCT_ID:
               return Response.SUCCESS.toString();
            case Pending.STRUCT_ID:
               return Pending.SUCCESS.toString();
            case Error.STRUCT_ID:
               return "ERROR";
         }
         
      }
      long key = makeKey(contractId, structId);
      Structure c = knownStructs.get(key);
      if (c != null) {
         return c.toString();
      }
      
      return String.format("<%d,%d>", contractId, structId);
   }

   public static List<StructDescription> getAllKnownStructures() {
      List<StructDescription> defs = new ArrayList<>();
      for (Structure s : knownStructs.values()) {
         defs.add(s.makeDescription());
      }
      return defs;
   }

}
