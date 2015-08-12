package io.tetrapod.core.storage;

import io.tetrapod.raft.*;
import io.tetrapod.raft.StateMachine.CommandFactory;

import java.io.*;

public class ClaimOwnershipCommand implements Command<TetrapodStateMachine> {

   public static final int COMMAND_ID = TetrapodStateMachine.CLAIM_OWNERSHIP_COMMAND_ID;

   private int             ownerId;
   private String          prefix;
   private String          key;
   private int             leaseMillis;
   private boolean         acquired;
   private long            curTime;

   public ClaimOwnershipCommand() {}

   public ClaimOwnershipCommand(int ownerId, String prefix, String key, int leaseMillis, long curTime) {
      this.ownerId = ownerId;
      this.prefix = prefix;
      this.key = key;
      this.leaseMillis = leaseMillis;
      this.curTime = curTime;
   }

   @Override
   public void applyTo(TetrapodStateMachine state) {
      acquired = state.claimOwnership(ownerId, prefix, leaseMillis, key, curTime);
   }

   @Override
   public void write(DataOutputStream out) throws IOException {
      out.writeInt(ownerId);
      out.writeUTF(prefix);
      out.writeUTF(key);
      out.writeInt(leaseMillis);
      out.writeLong(curTime);
      out.writeBoolean(acquired);
   }

   @Override
   public void read(DataInputStream in, int fileVersion) throws IOException {
      ownerId = in.readInt();
      if (fileVersion >= 3)
         prefix = in.readUTF();
      else
         prefix = "";
      key = in.readUTF();
      leaseMillis = in.readInt();
      curTime = in.readLong();
      acquired = in.readBoolean();
   }

   @Override
   public int getCommandType() {
      return COMMAND_ID;
   }

   public boolean wasAcquired() {
      return acquired;
   }

   @Override
   public String toString() {
      return "ClaimOwnership(" + ownerId + ", " + key + ", " + leaseMillis + ", " + acquired + ")";
   }

   public static void register(TetrapodStateMachine state) {
      state.registerCommand(COMMAND_ID, new CommandFactory<TetrapodStateMachine>() {
         @Override
         public Command<TetrapodStateMachine> makeCommand() {
            return new ClaimOwnershipCommand();
         }
      });
   }

   public String getKey() {
      return key;
   }

   public String getPrefix() {
      return prefix;
   }

   public int getOwnerId() {
      return ownerId;
   }

   public long getExpiry() {
      return curTime + leaseMillis;
   }
}
