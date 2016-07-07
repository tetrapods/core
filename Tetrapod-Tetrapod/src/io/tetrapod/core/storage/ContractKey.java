package io.tetrapod.core.storage;

public class ContractKey {
   private final int contractId;
   private final int subContractId;

   public ContractKey(int contractId, int subContractId) {
      this.contractId = contractId;
      this.subContractId = subContractId;
   }

   public int getContractId() {
      return contractId;
   }

   public int getSubContractId() {
      return subContractId;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      ContractKey that = (ContractKey) o;

      if (contractId != that.contractId)
         return false;
      return subContractId == that.subContractId;

   }

   @Override
   public int hashCode() {
      int result = contractId;
      result = 31 * result + subContractId;
      return result;
   }
}
