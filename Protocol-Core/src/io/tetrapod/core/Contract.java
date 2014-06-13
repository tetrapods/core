package io.tetrapod.core;

import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.WebRoute;

import java.lang.reflect.*;
import java.util.*;

abstract public class Contract {

   public static final int UNASSIGNED = 0;

   public Contract() {
      loadErrorCodes();
   }

   abstract public String getName();

   abstract public int getContractId();

   public Structure[] getRequests() {
      return new Structure[0];
   }

   public Structure[] getResponses() {
      return new Structure[0];
   }

   public Structure[] getMessages() {
      return new Structure[0];
   }

   public Structure[] getStructs() {
      return new Structure[0];
   }

   public WebRoute[] getWebRoutes() {
      return new WebRoute[] {};
   }

   public void registerStructs() {
      for (Structure s : getRequests()) {
         StructureFactory.add(s);
      }
      for (Structure s : getResponses()) {
         StructureFactory.add(s);
      }
      for (Structure s : getMessages()) {
         StructureFactory.add(s);
      }
      for (Structure s : getStructs()) {
         StructureFactory.add(s);
      }
   }

   public void registerPeerStructs() {
      for (Structure s : getResponses()) {
         StructureFactory.add(s);
      }
      for (Structure s : getMessages()) {
         StructureFactory.add(s);
      }
      for (Structure s : getStructs()) {
         StructureFactory.add(s);
      }
   }

   public static final Map<Long, String> ERROR_NAMES = new HashMap<>();

   private void loadErrorCodes() {
      for (Field f : getClass().getFields()) {
         final int mask = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;
         if ((f.getModifiers() & mask) == mask) {
            if (f.getType() == int.class) {
               if (f.getName().startsWith("ERROR_")) {
                  try {
                     int code = f.getInt(this);
                     long key = code | (long) ((long) getContractId() << 32);
                     ERROR_NAMES.put(key, f.getName());
                  } catch (IllegalArgumentException | IllegalAccessException e) {
                     throw new RuntimeException(e);
                  }
               }
            }
         }
      }
   }

   public static final String getErrorCode(int code, int contractId) {
      final long key = code | (long) ((long) contractId << 32);
      return ERROR_NAMES.get(key);
   }

}
