package io.tetrapod.core.storage;

import io.tetrapod.core.serialize.datasources.IOStreamDataSource;
import io.tetrapod.protocol.core.*;
import io.tetrapod.raft.Command;
import io.tetrapod.raft.StateMachine.CommandFactory;

import java.io.*;

public class SetWebRouteCommand implements Command<TetrapodStateMachine> {
   public static final int COMMAND_ID = TetrapodStateMachine.SET_WEB_ROUTE_COMMAND_ID;

   private WebRootDef      def;

   public SetWebRouteCommand() {}

   public SetWebRouteCommand(WebRootDef def) {
      this.def = def;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      state.setWebRoot(def, true);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      def.write(IOStreamDataSource.forWriting(out));
   }

   @Override
   public void read(DataInputStream in, int fileVersion) throws IOException {
      def = new WebRootDef();
      def.read(IOStreamDataSource.forReading(in));
   }

   @Override
   public int getCommandType() {
      return COMMAND_ID;
   }

   public WebRootDef getWebRouteDef() {
      return def;
   }
   
   public static void register(TetrapodStateMachine state) {
      state.registerCommand(COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new SetWebRouteCommand();
         }
      });
   }
}
