package io.tetrapod.core.storage;

import io.tetrapod.core.TetrapodService;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.raft.*;
import io.tetrapod.raft.*;
import io.tetrapod.raft.RaftEngine.Role;
import io.tetrapod.raft.storage.*;

import java.io.*;

import org.slf4j.*;

/**
 * Wraps a RaftEngine in our Tetrapod-RPC and implements the StorageContract via StorageStateMachine
 */
public class RaftStorage implements RaftRPC<StorageStateMachine>, io.tetrapod.protocol.raft.RaftContract.API {

   static final Logger                           logger = LoggerFactory.getLogger(RaftStorage.class);

   private final TetrapodService                 service;
   private final RaftEngine<StorageStateMachine> raft;
   private final Config                          cfg;

   public RaftStorage(TetrapodService service) {
      this.service = service;
      this.cfg = new Config().setLogDir(new File(Util.getProperty("raft.logs", "logs/raft"))).setClusterName(
            Util.getProperty("raft.name", "RaftStorage"));

      RaftEngine<StorageStateMachine> raftEngine = null;
      try {
         raftEngine = new RaftEngine<StorageStateMachine>(cfg, new StorageStateMachine.Factory(), this);
      } catch (IOException e) {
         service.fail(e);
      }
      this.raft = raftEngine;
   }

   public void start() {
      raft.setPeerId(service.getEntityId());
      raft.start();
   }

   public void stop() {
      raft.stop();
   }

   public void addMember(int entityId) {
      raft.addPeer(entityId);
   }

   @Override
   public void sendRequestVote(String clusterName, int peerId, long term, int candidateId, long lastLogIndex, long lastLogTerm,
         final io.tetrapod.raft.RaftRPC.VoteResponseHandler handler) {

      service.sendRequest(new io.tetrapod.protocol.raft.VoteRequest(clusterName, term, candidateId, lastLogIndex, lastLogTerm), peerId)
            .handle(new ResponseHandler() {
               @Override
               public void onResponse(Response res) {
                  if (res.isError()) {
                     logger.error("{}", res);
                  } else {
                     io.tetrapod.protocol.raft.VoteResponse r = (io.tetrapod.protocol.raft.VoteResponse) res;
                     handler.handleResponse(r.term, r.voteGranted);
                  }
               }
            });

   }

   @Override
   public void sendAppendEntries(int peerId, long term, int leaderId, long prevLogIndex, long prevLogTerm,
         Entry<StorageStateMachine>[] entries, long leaderCommit, final io.tetrapod.raft.RaftRPC.AppendEntriesResponseHandler handler) {

      final LogEntry[] entryList = entries == null ? null : new LogEntry[entries.length];
      if (entryList != null) {
         try {
            int i = 0;
            for (Entry<StorageStateMachine> e : entries) {
               byte[] data = commandToBytes(e.getCommand());
               entryList[i++] = new LogEntry(e.getTerm(), e.getIndex(), e.getCommand().getCommandType(), data);
            }
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      }

      service.sendRequest(
            new io.tetrapod.protocol.raft.AppendEntriesRequest(term, leaderId, prevLogIndex, prevLogTerm, entryList, leaderCommit), peerId)
            .handle(new ResponseHandler() {
               @Override
               public void onResponse(Response res) {
                  if (res.isError()) {
                     logger.error("{}", res);
                  } else {
                     io.tetrapod.protocol.raft.AppendEntriesResponse r = (io.tetrapod.protocol.raft.AppendEntriesResponse) res;
                     handler.handleResponse(r.term, r.success, r.lastLogIndex);
                  }
               }
            });

   }

   private byte[] commandToBytes(Command<?> command) throws IOException {
      try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
         try (DataOutputStream out = new DataOutputStream(buf)) {
            command.write(out);
            return buf.toByteArray();
         }
      }
   }

   private Command<StorageStateMachine> bytesToCommand(byte[] data, int type) throws IOException {
      Command<StorageStateMachine> cmd = (Command<StorageStateMachine>) raft.getStateMachine().makeCommandById(type);
      try (ByteArrayInputStream buf = new ByteArrayInputStream(data)) {
         try (DataInputStream in = new DataInputStream(buf)) {
            cmd.read(in);
            return cmd;
         }
      }
   }

   @Override
   public Response requestVote(VoteRequest r, RequestContext ctx) {

      // HACK: A rude way to do cluster joins
      if (r.clusterName.equals(cfg.getClusterName())) {
         if (!raft.isValidPeer(r.candidateId)) {
            raft.addPeer(r.candidateId);
         }
      }

      final VoteResponse res = new VoteResponse();
      raft.handleVoteRequest(r.clusterName, r.term, r.candidateId, r.lastLogIndex, r.lastLogTerm, new VoteResponseHandler() {
         @Override
         public void handleResponse(long term, boolean voteGranted) {
            res.term = term;
            res.voteGranted = voteGranted;
         }
      });
      return res;
   }

   @Override
   public Response requestAppendEntries(AppendEntriesRequest r, RequestContext ctx) {
      final AppendEntriesResponse res = new AppendEntriesResponse();

      @SuppressWarnings("unchecked")
      Entry<StorageStateMachine>[] entries = r.entries == null ? null : (Entry<StorageStateMachine>[]) new Entry<?>[r.entries.length];
      if (entries != null) {
         try {
            int i = 0;
            for (LogEntry e : r.entries) {
               Command<StorageStateMachine> cmd = bytesToCommand(e.command, e.type);
               entries[i++] = new Entry<StorageStateMachine>(e.term, e.index, cmd);
            }
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      }

      raft.handleAppendEntriesRequest(r.term, r.leaderId, r.prevLogIndex, r.prevLogTerm, entries, r.leaderCommit,
            new AppendEntriesResponseHandler() {
               @Override
               public void handleResponse(long term, boolean success, long lastLogIndex) {
                  res.term = term;
                  res.success = success;
                  res.lastLogIndex = lastLogIndex;
               }
            });
      return res;
   }

   @Override
   public void sendInstallSnapshot(int peerId, long term, long index, long length, int partSize, int part, byte[] data,
         io.tetrapod.raft.RaftRPC.InstallSnapshotResponseHandler handler) {

   }

   @Override
   public Response genericRequest(Request r, RequestContext ctx) {
      return null;
   }

   public void logStatus() {
      logger.info(String.format("#%d: %9s term=%d, lastIndex=%d, lastTerm=%d commitIndex=%d, %s, peers=%d", raft.getPeerId(), raft
            .getRole(), raft.getCurrentTerm(), raft.getLog().getLastIndex(), raft.getLog().getLastTerm(), raft.getLog().getCommitIndex(),
            raft.getStateMachine(), raft.getClusterSize()));

      if (raft.getRole() == Role.Leader) {
         // HACK: generate some command activity for testing
         raft.executeCommand(new PutItemCommand("foo", ("bar-" + raft.getLog().getCommitIndex()).getBytes()),
               new ClientResponseHandler<StorageStateMachine>() {
                  @Override
                  public void handleResponse(boolean success, Command<StorageStateMachine> command) {

                  }
               });
      }
   }
}
