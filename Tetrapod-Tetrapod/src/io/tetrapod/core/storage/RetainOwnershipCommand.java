package io.tetrapod.core.storage;

import io.tetrapod.raft.*;
import io.tetrapod.raft.StateMachine.CommandFactory;

import java.io.*;

public class RetainOwnershipCommand implements Command<TetrapodStateMachine> {

   public static final int COMMAND_ID = TetrapodStateMachine.RETAIN_OWNERSHIP_COMMAND_ID;

   private int             ownerId;
   private int             leaseMillis;
   private long            curTime;
   private String          prefix;

   public RetainOwnershipCommand() {}

   public RetainOwnershipCommand(int ownerId, String prefix, int leaseMillis, long curTime) {
      this.ownerId = ownerId;
      this.leaseMillis = leaseMillis;
      this.curTime = curTime;
      this.prefix = prefix;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      state.retainOwnership(ownerId, leaseMillis, curTime);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      out.writeInt(ownerId);
      out.writeUTF(prefix);
      out.writeInt(leaseMillis);
      out.writeLong(curTime);
   }

   @Override
   public void read(DataInputStream in, int fileVersion) throws IOException {
      ownerId = in.readInt();
      prefix = in.readUTF();
      leaseMillis = in.readInt();
      curTime = in.readLong();
   }

   @Override
   public int getCommandType() {
      return COMMAND_ID;
   }

   @Override
   public String toString() {
      return "RetainOwnershipCommand(" + ownerId + ", " + prefix + ", " + leaseMillis + ")";
   }

   public static void register(TetrapodStateMachine state) {
      state.registerCommand(COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new RetainOwnershipCommand();
         }
      });
   }

   public int getOwnerId() {
      return ownerId;
   }

   public String getPrefix() {
      return prefix;
   }

   public long getExpiry() {
      return curTime + leaseMillis;
   }
}
