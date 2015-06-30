package io.tetrapod.core.storage;

import io.tetrapod.core.serialize.datasources.IOStreamDataSource;
import io.tetrapod.protocol.core.*;
import io.tetrapod.raft.Command;
import io.tetrapod.raft.StateMachine.CommandFactory;

import java.io.*;

public class AddAdminUserCommand implements Command<TetrapodStateMachine> {
   public static final int COMMAND_ID = TetrapodStateMachine.ADD_ADMIN_COMMAND_ID;

   private Admin           admin;

   public AddAdminUserCommand() {}

   public AddAdminUserCommand(Admin admin) {
      this.admin = admin;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      state.addAdminUser(admin, true);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      admin.write(IOStreamDataSource.forWriting(out));
   }

   @Override
   public void read(DataInputStream in) throws IOException {
      admin = new Admin();
      admin.read(IOStreamDataSource.forReading(in));
   }

   @Override
   public int getCommandType() {
      return COMMAND_ID;
   }

   public Admin getAdminUser() {
      return admin;
   }

   public static void register(TetrapodStateMachine state) {
      state.registerCommand(COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new AddAdminUserCommand();
         }
      });
   }
}
