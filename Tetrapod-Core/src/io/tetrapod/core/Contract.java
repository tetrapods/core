package io.tetrapod.core;

import io.tetrapod.protocol.core.WebRoute;


abstract public class Contract {

   public static final int UNASSIGNED = 0;

   abstract public String getName();

   abstract public int getContractId();

   public void addRequests(StructureFactory factory, int dynamicId) {
   }

   public void addResponses(StructureFactory factory, int dynamicId) {
   }
   
   public void addMessages(StructureFactory factory, int dynamicId) {
   }
   
   public WebRoute[] getWebRoutes() {
      return new WebRoute[] {};
   }
   
}
