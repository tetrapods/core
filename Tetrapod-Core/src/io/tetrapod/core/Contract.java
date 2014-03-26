package io.tetrapod.core;

import io.tetrapod.protocol.core.WebRoute;


abstract public class Contract {

   public static final int UNASSIGNED = 0;

   abstract public String getName();

   abstract public int getContractId();

   public void addRequests(StructureFactory factory) {
   }

   public void addResponses(StructureFactory factory) {
   }
   
   public void addMessages(StructureFactory factory) {
   }
   
   public WebRoute[] getWebRoutes() {
      return new WebRoute[] {};
   }
   
}
