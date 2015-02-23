package io.tetrapod.core.storage;

import io.tetrapod.core.registry.Registry;
import io.tetrapod.raft.*;
import io.tetrapod.raft.storage.*;

import java.io.*;

public class TetrapodStateMachine extends StorageStateMachine<TetrapodStateMachine> {

   public static final String KEY_TETRAPOD_COUNTER = "tetrapod.entity.counter";
   public static final String KEY_TETRAPOD_ID      = "tetrapod.entity::";

   public static class Factory implements StateMachine.Factory<TetrapodStateMachine> {
      public TetrapodStateMachine makeStateMachine() {
         return new TetrapodStateMachine();
      }
   }

   public TetrapodStateMachine() {
      super();
      registerCommand(IssueTetrapodEntityIdCommand.COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new IssueTetrapodEntityIdCommand();
         }
      });
   }

   public static class IssueTetrapodEntityIdCommand implements Command<TetrapodStateMachine> {
      public static final int COMMAND_ID = 1000;

      public int              entityId;

      public IssueTetrapodEntityIdCommand() {}

      public IssueTetrapodEntityIdCommand(int entityId) {
         this.entityId = entityId;
      }

      @Override
      public void applyTo(TetrapodStateMachine state) {
         this.entityId = state.issueTetrapodId() << Registry.PARENT_ID_SHIFT;
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeInt(entityId);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         entityId = in.readInt();
      }

      @Override
      public int getCommandType() {
         return COMMAND_ID;
      }
   }

   public int issueTetrapodId() {
      int tetrapodId = 1;
      StorageItem item = getItem(KEY_TETRAPOD_COUNTER);
      if (item != null) {
         tetrapodId = (int) (RaftUtil.toLong(item.getData()));
         do {
            tetrapodId = (1 + tetrapodId) % Registry.MAX_PARENTS;
         } while (items.containsKey(KEY_TETRAPOD_ID + tetrapodId));
         modify(item, RaftUtil.toBytes(tetrapodId));
      } else {
         items.put(KEY_TETRAPOD_COUNTER, new StorageItem(KEY_TETRAPOD_COUNTER, RaftUtil.toBytes(tetrapodId)));
      }
      // store a key for this tetrapodId being in active use:
      items.put(KEY_TETRAPOD_ID + tetrapodId, new StorageItem(KEY_TETRAPOD_ID + tetrapodId, RaftUtil.toBytes(System.currentTimeMillis())));
      return tetrapodId;
   }

}
