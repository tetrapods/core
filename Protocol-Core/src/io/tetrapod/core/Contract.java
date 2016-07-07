package io.tetrapod.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.WebRoute;

abstract public class Contract {

   public static final int UNASSIGNED = 0;

   public Contract() {
      loadErrorCodes();
   }

   abstract public String getName();

   abstract public int getContractId();

   abstract public int getSubContractId();

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
                        long code = f.getInt(this);
                        ERROR_NAMES.put(code | (long) ((long) getContractId() << 32), f.getName());
                        if (ERROR_NAMES.containsKey(code)) {
                           if (!ERROR_NAMES.get(code).equals(f.getName())) {
                              System.err.println("ERROR code collision with " + code + ": " + ERROR_NAMES.get(code) + " != " + f.getName());
                           }
                        } else {
                           ERROR_NAMES.put(code, f.getName());
                        }
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
         final long key = code;// | (long) ((long) contractId << 32);
         final String name = ERROR_NAMES.get(key);
         //         if (name == null && contractId != Core.CONTRACT_ID) {
         //            return getErrorCode(code, Core.CONTRACT_ID);
         //         }
         return name == null ? "#" + code : name;
      }
   }

   public static final String getErrorCode(int code) {
      synchronized (ERROR_NAMES) {
         final String name = ERROR_NAMES.get((long) code);
         return name == null ? "#" + code : name;
      }
   }

}
