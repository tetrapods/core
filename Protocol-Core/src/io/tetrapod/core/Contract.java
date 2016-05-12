package io.tetrapod.core;

import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.Core;
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

   abstract public int getContractVersion();

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

   public static final Map<Long, String> ERROR_NAMES = new HashMap<>();

   private void loadErrorCodes() {
      synchronized (ERROR_NAMES) {
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
   }

   public static final String getErrorCode(int code, int contractId) {
      synchronized (ERROR_NAMES) {
         final long key = code | (long) ((long) contractId << 32);
         final String name = ERROR_NAMES.get(key);
         if (name == null && contractId != Core.CONTRACT_ID) {
            return getErrorCode(code, Core.CONTRACT_ID);
         }
         return name == null ? "#" + code : name;
      }
   }

}
