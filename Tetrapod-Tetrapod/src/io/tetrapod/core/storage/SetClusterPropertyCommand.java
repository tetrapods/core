package io.tetrapod.core.storage;

import io.tetrapod.core.serialize.datasources.IOStreamDataSource;
import io.tetrapod.protocol.core.ClusterProperty;
import io.tetrapod.raft.*;
import io.tetrapod.raft.StateMachine.CommandFactory;

import java.io.*;

public class SetClusterPropertyCommand implements Command<TetrapodStateMachine> {
   public static final int COMMAND_ID = TetrapodStateMachine.SET_CLUSTER_PROPERTY_COMMAND_ID;

   private ClusterProperty prop;

   public SetClusterPropertyCommand() {}

   public SetClusterPropertyCommand(ClusterProperty prop) {
      this.prop = prop;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      state.setProperty(prop, true);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      prop.write(IOStreamDataSource.forWriting(out));
   }

   @Override
   public void read(DataInputStream in, int fileVersion) throws IOException {
      prop = new ClusterProperty();
      prop.read(IOStreamDataSource.forReading(in));
   }

   @Override
   public int getCommandType() {
      return COMMAND_ID;
   }

   public ClusterProperty getProperty() {
      return prop;
   }

   public static void register(TetrapodStateMachine state) {
      state.registerCommand(COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new SetClusterPropertyCommand();
         }
      });
   }
}
