package io.tetrapod.core;


abstract public class Contract {

   public static final int UNASSIGNED = 0;

   public void addRequests(StructureFactory factory, int dynamicId) {
   }

   public void addResponses(StructureFactory factory, int dynamicId) {
   }
   
   public void addMessages(StructureFactory factory, int dynamicId) {
   }
   
   abstract public String getName();
   
   public void setContractId(int id) {
   }
}
