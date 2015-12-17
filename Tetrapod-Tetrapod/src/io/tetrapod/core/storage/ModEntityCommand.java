package io.tetrapod.core.storage;

import java.io.*;

import io.tetrapod.raft.Command;

public class ModEntityCommand implements Command<TetrapodStateMachine> {

   public static final int COMMAND_ID     = TetrapodStateMachine.MOD_ENTITY_COMMAND_ID;
   private final byte      commandVersion = 2;

   private int             entityId;
   private int             status;
   private int             mask;
   private String          build;
   private int             version;

   public ModEntityCommand() {}

   public ModEntityCommand(int entityId, int status, int mask, String build, int version) {
      this.entityId = entityId;
      this.status = status;
      this.mask = mask;
      this.build = build;
      this.version = version;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      state.updateEntity(entityId, status, mask, build, version);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      out.writeByte(commandVersion);
      out.writeInt(entityId);
      out.writeInt(status);
      out.writeInt(mask);
      out.writeUTF(build);
      out.writeInt(version);
   }

   @Override
   public void read(DataInputStream in, int fileVersion) throws IOException {
      byte commandVersion = in.readByte();
      assert commandVersion <= this.commandVersion;
      entityId = in.readInt();
      status = in.readInt();
      if (commandVersion >= 2) {
         mask = in.readInt();
      } else {
         mask = 0xFFFFFFFF;
      }
      build = in.readUTF();
      version = in.readInt();
   }

   @Override
   public int getCommandType() {
      return COMMAND_ID;
   }

   @Override
   public String toString() {
      return "ModEntityCommand(" + entityId + ", " + status + ", " + mask + ", " + build + ", " + version + ")";
   }

   public static void register(TetrapodStateMachine state) {
      state.registerCommand(COMMAND_ID, () -> new ModEntityCommand());
   }

   public int getEntityId() {
      return entityId;
   }

   public int getStatus() {
      return status;
   }

   public int getMask() {
      return mask;
   }

}
