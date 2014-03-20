package io.tetrapod.core;


abstract public class Contract {

   public void addRequests(StructureFactory factory, int dynamicId) {
   }

   public void addResponses(StructureFactory factory, int dynamicId) {
   }
   
   public void addMessages(StructureFactory factory, int dynamicId) {
   }
   
   abstract public String getName();
}
