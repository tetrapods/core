package io.tetrapod.core.storage;

import io.tetrapod.core.TetrapodService;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.storage.TetrapodStateMachine.IssueTetrapodEntityIdCommand;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.raft.*;
import io.tetrapod.raft.*;
import io.tetrapod.raft.RaftEngine.Role;
import io.tetrapod.raft.storage.PutItemCommand;

import java.io.*;

import org.slf4j.*;

import com.hazelcast.core.ILock;

/**
 * Wraps a RaftEngine in our Tetrapod-RPC and implements the StorageContract via TetrapodStateMachine
 */
public class RaftStorage extends Storage implements RaftRPC<TetrapodStateMachine>, RaftContract.API {

   private static final Logger                    logger = LoggerFactory.getLogger(RaftStorage.class);

   private final TetrapodService                  service;
   private final RaftEngine<TetrapodStateMachine> raft;
   private final Config                           cfg;

   public RaftStorage(TetrapodService service) {
      this.service = service;
      this.cfg = new Config().setLogDir(new File(Util.getProperty("raft.logs", "logs/raft"))).setClusterName(
            Util.getProperty("raft.name", "RaftStorage"));

      RaftEngine<TetrapodStateMachine> raftEngine = null;
      try {
         raftEngine = new RaftEngine<TetrapodStateMachine>(cfg, new TetrapodStateMachine.Factory(), this);
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

   @Override
   public void shutdown() {
      stop();
   }

   public void addMember(int entityId) {
      raft.addPeer(entityId);
   }

   @Override
   public void sendRequestVote(String clusterName, int peerId, long term, int candidateId, long lastLogIndex, long lastLogTerm,
         final VoteResponseHandler handler) {

      service.sendRequest(new VoteRequest(clusterName, term, candidateId, lastLogIndex, lastLogTerm), peerId).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            if (res.isError()) {
               logger.error("{}", res);
            } else {
               VoteResponse r = (VoteResponse) res;
               handler.handleResponse(r.term, r.voteGranted);
            }
         }
      });

   }

   @Override
   public void sendAppendEntries(int peerId, long term, int leaderId, long prevLogIndex, long prevLogTerm,
         Entry<TetrapodStateMachine>[] entries, long leaderCommit, final AppendEntriesResponseHandler handler) {

      final LogEntry[] entryList = entries == null ? null : new LogEntry[entries.length];
      if (entryList != null) {
         try {
            int i = 0;
            for (Entry<TetrapodStateMachine> e : entries) {
               byte[] data = commandToBytes(e.getCommand());
               entryList[i++] = new LogEntry(e.getTerm(), e.getIndex(), e.getCommand().getCommandType(), data);
            }
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      }

      service.sendRequest(new AppendEntriesRequest(term, leaderId, prevLogIndex, prevLogTerm, entryList, leaderCommit), peerId).handle(
            new ResponseHandler() {
               @Override
               public void onResponse(Response res) {
                  if (res.isError()) {
                     logger.error("{}", res);
                  } else {
                     AppendEntriesResponse r = (AppendEntriesResponse) res;
                     handler.handleResponse(r.term, r.success, r.lastLogIndex);
                  }
               }
            });

   }

   @Override
   public void sendInstallSnapshot(int peerId, long term, long index, long length, int partSize, int part, byte[] data,
         final InstallSnapshotResponseHandler handler) {
      service.sendRequest(new InstallSnapshotRequest(term, index, length, partSize, part, data), peerId).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            if (res.isError()) {
               logger.error("{}", res);
            } else {
               InstallSnapshotResponse r = (InstallSnapshotResponse) res;
               handler.handleResponse(r.success);
            }
         }
      });
   }

   @Override
   public void sendIssueCommand(int peerId, final Command<TetrapodStateMachine> command,
         final ClientResponseHandler<TetrapodStateMachine> handler) {
      try {
         final byte[] data = commandToBytes(command);
         service.sendRequest(new IssueCommandRequest(command.getCommandType(), data), peerId).handle(new ResponseHandler() {
            @Override
            public void onResponse(Response res) {
               if (res.isError()) {
                  logger.error("{}", res);
               } else {
                  IssueCommandResponse r = (IssueCommandResponse) res;
                  try {
                     handler.handleResponse(bytesToCommand(r.command, command.getCommandType()));
                  } catch (IOException e) {
                     logger.error(e.getMessage(), e);
                  }
               }
            }
         });
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   private byte[] commandToBytes(Command<?> command) throws IOException {
      if (command == null)
         return null;
      try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
         try (DataOutputStream out = new DataOutputStream(buf)) {
            command.write(out);
            return buf.toByteArray();
         }
      }
   }

   private Command<TetrapodStateMachine> bytesToCommand(byte[] data, int type) throws IOException {
      if (data == null)
         return null;
      Command<TetrapodStateMachine> cmd = (Command<TetrapodStateMachine>) raft.getStateMachine().makeCommandById(type);
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
      Entry<TetrapodStateMachine>[] entries = r.entries == null ? null : (Entry<TetrapodStateMachine>[]) new Entry<?>[r.entries.length];
      if (entries != null) {
         try {
            int i = 0;
            for (LogEntry e : r.entries) {
               Command<TetrapodStateMachine> cmd = bytesToCommand(e.command, e.type);
               entries[i++] = new Entry<TetrapodStateMachine>(e.term, e.index, cmd);
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
   public Response requestInstallSnapshot(InstallSnapshotRequest r, RequestContext ctx) {
      final InstallSnapshotResponse res = new InstallSnapshotResponse();
      raft.handleInstallSnapshotRequest(r.term, r.index, r.length, r.partSize, r.part, r.data, new InstallSnapshotResponseHandler() {
         @Override
         public void handleResponse(boolean success) {
            res.success = success;
         }
      });
      return res;
   }

   @Override
   public Response requestIssueCommand(IssueCommandRequest r, RequestContext ctx) {
      final IssueCommandResponse res = new IssueCommandResponse();
      try {
         raft.handleClientRequest(bytesToCommand(r.command, r.type), new ClientResponseHandler<TetrapodStateMachine>() {
            @Override
            public void handleResponse(Command<TetrapodStateMachine> command) {
               try {
                  res.command = commandToBytes(command);
               } catch (IOException e) {
                  logger.error(e.getMessage(), e);
               }
            }
         });
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
      return res;
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
         raft.executeCommand(new PutItemCommand<TetrapodStateMachine>("foo", ("bar-" + raft.getLog().getCommitIndex()).getBytes()), null);
      }
   }

   public synchronized int issueTetrapodId() {
      final IssueTetrapodEntityIdCommand cmd = new IssueTetrapodEntityIdCommand();
      if (raft.getRole() == Role.Leader) {
         if (raft.executeCommand(cmd, null)) {
            return cmd.entityId;
         }
      }
      // Send RPC to leader
      sendIssueCommand(raft.getLeader(), cmd, new ClientResponseHandler<TetrapodStateMachine>() {
         @Override
         public void handleResponse(Command<TetrapodStateMachine> command) {
            if (command != null) {
               int e = ((IssueTetrapodEntityIdCommand) command).entityId;
            }
         }
      });

      return 0;
   }

   @Override
   public void put(String key, String value) {
      // TODO Auto-generated method stub
   }

   @Override
   public String delete(String key) {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public String get(String key) {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public ILock getLock(String lockKey) {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public long increment(String key) {
      // TODO Auto-generated method stub
      return 0;
   }

}
