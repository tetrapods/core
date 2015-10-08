package io.tetrapod.core.storage;

import java.io.*;

import io.tetrapod.raft.Command;
import io.tetrapod.raft.StateMachine.CommandFactory;

public class ModEntityCommand implements Command<TetrapodStateMachine> {

   public static final int COMMAND_ID = TetrapodStateMachine.MOD_ENTITY_COMMAND_ID;

   private final byte commandVersion = 1;

   private int entityId;
   private int status;
   private int build;
   private int version;

   public ModEntityCommand() {}

   public ModEntityCommand(int entityId, int status, int build, int version) {
      this.entityId = entityId;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      state.updateEntity(entityId, status, build, version);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      out.writeByte(commandVersion);
      out.writeInt(entityId);
      out.writeInt(status);
      out.writeInt(build);
      out.writeInt(version);
   }

   @Override
   public void read(DataInputStream in, int fileVersion) throws IOException {
      byte commandVersion = in.readByte();
      assert commandVersion == this.commandVersion;
      entityId = in.readInt();
      status = in.readInt();
      build = in.readInt();
      version = in.readInt();
   }

   @Override
   public int getCommandType() {
      return COMMAND_ID;
   }

   @Override
   public String toString() {
      return "ModEntityCommand(" + entityId + ", " + status + ", " + build + ", " + version + ")";
   }

   public static void register(TetrapodStateMachine state) {
      state.registerCommand(COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new ModEntityCommand();
         }
      });
   }

   public int getEntityId() {
      return entityId;
   }

}
