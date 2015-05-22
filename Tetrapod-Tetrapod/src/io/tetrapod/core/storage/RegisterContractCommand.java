package io.tetrapod.core.storage;

import io.tetrapod.core.serialize.datasources.IOStreamDataSource;
import io.tetrapod.protocol.core.*;
import io.tetrapod.raft.Command;

import java.io.*;

public class RegisterContractCommand implements Command<TetrapodStateMachine> {
   public static final int     COMMAND_ID = 402;

   private ContractDescription info;

   public RegisterContractCommand() {}

   public RegisterContractCommand(ContractDescription info) {
      this.info = info;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      state.registerContract(info);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      info.write(IOStreamDataSource.forWriting(out));
   }

   @Override
   public void read(DataInputStream in) throws IOException {
      info = new ContractDescription();
      info.read(IOStreamDataSource.forReading(in));
   }

   @Override
   public int getCommandType() {
      return COMMAND_ID;
   }

   public ContractDescription getContractDescription() {
      return info;
   }
}
