package io.tetrapod.core;


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
   
   public void setContractId(int id) {
   }
   
   public boolean isCoreContract() {
      return getContractId() < 10;
   }
}
