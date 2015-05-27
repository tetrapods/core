package io.tetrapod.core.storage;

import io.tetrapod.core.serialize.datasources.IOStreamDataSource;
import io.tetrapod.protocol.core.*;
import io.tetrapod.raft.Command;

import java.io.*;

public class DelWebRouteCommand implements Command<TetrapodStateMachine> {
   public static final int COMMAND_ID = TetrapodStateMachine.DEL_WEB_ROUTE_COMMAND_ID;

   private String          name;

   public DelWebRouteCommand() {}

   public DelWebRouteCommand(String name) {
      this.name = name;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      state.delWebRoot(name);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      out.writeUTF(name);
   }

   @Override
   public void read(DataInputStream in) throws IOException {
      name = in.readUTF();
   }

   @Override
   public int getCommandType() {
      return COMMAND_ID;
   }

   public String getWebRouteName() {
      return name;
   }
}
