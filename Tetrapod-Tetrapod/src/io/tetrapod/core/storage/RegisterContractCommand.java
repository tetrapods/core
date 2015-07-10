package io.tetrapod.core.storage;

import io.tetrapod.core.serialize.datasources.IOStreamDataSource;
import io.tetrapod.protocol.core.*;
import io.tetrapod.raft.Command;
import io.tetrapod.raft.StateMachine.CommandFactory;

import java.io.*;

public class RegisterContractCommand implements Command<TetrapodStateMachine> {
   public static final int     COMMAND_ID = TetrapodStateMachine.REGISTER_CONTRACT_COMMAND_ID;

   private ContractDescription info;

   public RegisterContractCommand() {}

   public RegisterContractCommand(ContractDescription info) {
      this.info = info;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      state.registerContract(info, true);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      info.write(IOStreamDataSource.forWriting(out));
   }

   @Override
   public void read(DataInputStream in, int fileVersion) throws IOException {
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

   public static void register(TetrapodStateMachine state) {
      state.registerCommand(COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new RegisterContractCommand();
         }
      });
   }
}
