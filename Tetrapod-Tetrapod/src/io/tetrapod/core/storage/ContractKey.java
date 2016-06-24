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
}
