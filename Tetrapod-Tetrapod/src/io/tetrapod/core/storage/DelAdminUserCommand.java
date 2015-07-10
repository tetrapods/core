package io.tetrapod.core.storage;

import io.tetrapod.raft.Command;
import io.tetrapod.raft.StateMachine.CommandFactory;

import java.io.*;

public class DelAdminUserCommand implements Command<TetrapodStateMachine> {
   public static final int COMMAND_ID = TetrapodStateMachine.DEL_ADMIN_COMMAND_ID;

   private int             accountId;

   public DelAdminUserCommand() {}

   public DelAdminUserCommand(int accountId) {
      this.accountId = accountId;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      state.delAdminUser(accountId);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      out.writeInt(accountId);
   }

   @Override
   public void read(DataInputStream in, int fileVersion) throws IOException {
      accountId = in.readInt();
   }

   @Override
   public int getCommandType() {
      return COMMAND_ID;
   }

   public int getAccountId() {
      return accountId;
   }

   public static void register(TetrapodStateMachine state) {
      state.registerCommand(COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new DelAdminUserCommand();
         }
      });
   }
}
