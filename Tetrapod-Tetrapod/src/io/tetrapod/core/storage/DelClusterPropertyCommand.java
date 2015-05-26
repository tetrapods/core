package io.tetrapod.core.storage;

import io.tetrapod.raft.Command;

import java.io.*;

public class DelClusterPropertyCommand implements Command<TetrapodStateMachine> {
   public static final int COMMAND_ID = TetrapodStateMachine.DEL_CLUSTER_PROPERTY_COMMAND_ID;

   private String          key;

   public DelClusterPropertyCommand() {}

   public DelClusterPropertyCommand(String key) {
      this.key = key;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      state.delProperty(key);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      out.writeUTF(key);
   }

   @Override
   public void read(DataInputStream in) throws IOException {
      key = in.readUTF();
   }

   @Override
   public int getCommandType() {
      return COMMAND_ID;
   }

   public String getProperty() {
      return key;
   }
}
