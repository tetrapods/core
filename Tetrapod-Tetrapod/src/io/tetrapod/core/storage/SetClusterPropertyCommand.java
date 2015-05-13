package io.tetrapod.core.storage;

import io.tetrapod.core.serialize.datasources.IOStreamDataSource;
import io.tetrapod.protocol.core.ClusterProperty;
import io.tetrapod.raft.Command;

import java.io.*;

public class SetClusterPropertyCommand implements Command<TetrapodStateMachine> {
   public static final int COMMAND_ID = 400;

   private ClusterProperty prop;

   public SetClusterPropertyCommand() {}

   public SetClusterPropertyCommand(ClusterProperty prop) {
      this.prop = prop;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      // state.setProperty(prop);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      prop.write(IOStreamDataSource.forWriting(out));
   }

   @Override
   public void read(DataInputStream in) throws IOException {
      prop.read(IOStreamDataSource.forReading(in));
   }

   @Override
   public int getCommandType() {
      return COMMAND_ID;
   }

}
