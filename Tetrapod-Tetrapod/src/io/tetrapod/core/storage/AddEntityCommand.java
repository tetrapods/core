package io.tetrapod.core.storage;

import java.io.*;

import io.tetrapod.core.serialize.datasources.IOStreamDataSource;
import io.tetrapod.protocol.core.Entity;
import io.tetrapod.raft.Command;

public class AddEntityCommand implements Command<TetrapodStateMachine> {

   public static final int COMMAND_ID = TetrapodStateMachine.ADD_ENTITY_COMMAND_ID;

   private Entity entity;

   public AddEntityCommand() {}

   public AddEntityCommand(Entity e) {
      this.entity = e;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      state.addEntity(entity, true);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      entity.write(IOStreamDataSource.forWriting(out));
   }

   @Override
   public void read(DataInputStream in, int fileVersion) throws IOException {
      entity = new Entity();
      entity.read(IOStreamDataSource.forReading(in));
   }

   @Override
   public int getCommandType() {
      return COMMAND_ID;
   }

   @Override
   public String toString() {
      return "AddEntityCommand(" + entity.dump() + ")";
   }

   public static void register(TetrapodStateMachine state) {
      state.registerCommand(COMMAND_ID, () -> new AddEntityCommand());
   }

   public Entity getEntity() {
      return entity;
   }

}
