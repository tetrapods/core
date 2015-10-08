package io.tetrapod.core.storage;

import java.io.*;

import io.tetrapod.raft.Command;
import io.tetrapod.raft.StateMachine.CommandFactory;

public class DelEntityCommand implements Command<TetrapodStateMachine> {

   public static final int COMMAND_ID = TetrapodStateMachine.DEL_ENTITY_COMMAND_ID;

   private int entityId;

   public DelEntityCommand() {}

   public DelEntityCommand(int entityId) {
      this.entityId = entityId;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      state.delEntity(entityId);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      out.writeInt(entityId);
   }

   @Override
   public void read(DataInputStream in, int fileVersion) throws IOException {
      entityId = in.readInt();
   }

   @Override
   public int getCommandType() {
      return COMMAND_ID;
   }

   @Override
   public String toString() {
      return "DelEntityCommand(" + entityId + ")";
   }

   public static void register(TetrapodStateMachine state) {
      state.registerCommand(COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new DelEntityCommand();
         }
      });
   }

   public int getEntityId() {
      return entityId;
   }

}
