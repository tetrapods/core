package io.tetrapod.core;

import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.WebRoute;


abstract public class Contract {

   public static final int UNASSIGNED = 0;

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

}
