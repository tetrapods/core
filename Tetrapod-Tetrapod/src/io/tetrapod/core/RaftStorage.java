package io.tetrapod.core;

import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.registry.Registry;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.storage.Storage;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.raft.*;
import io.tetrapod.raft.*;
import io.tetrapod.raft.RaftEngine.Role;
import io.tetrapod.raft.storage.PutItemCommand;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.*;

import com.hazelcast.core.ILock;

/**
 * Wraps a RaftEngine in our Tetrapod-RPC and implements the StorageContract via TetrapodStateMachine
 */
public class RaftStorage extends Storage implements RaftRPC<TetrapodStateMachine>, RaftContract.API,
      StateMachine.Listener<TetrapodStateMachine>, SessionFactory {

   private static final Logger                    logger  = LoggerFactory.getLogger(RaftStorage.class);

   private final SecureRandom                     random  = new SecureRandom();

   private final Server                           server;

   /**
    * Maps EntityId to TetrapodPeer
    */
   private final Map<Integer, TetrapodPeer>       cluster = new ConcurrentHashMap<>();

   private final TetrapodService                  service;

   private final RaftEngine<TetrapodStateMachine> raft;

   private final Config                           cfg;

   public RaftStorage(TetrapodService service) {
      this.service = service;

      this.server = new Server(service.getClusterPort(), this, service.getDispatcher());

      this.cfg = new Config().setLogDir(new File(Util.getProperty("raft.logs", "logs/raft"))).setClusterName(
            Util.getProperty("raft.name", "RaftStorage"));

      RaftEngine<TetrapodStateMachine> raftEngine = null;
      try {
         raftEngine = new RaftEngine<TetrapodStateMachine>(cfg, new TetrapodStateMachine.Factory(), this);
      } catch (IOException e) {
         service.fail(e);
      }
      this.raft = raftEngine;
      this.raft.getStateMachine().addListener(this);

      // FIXME: build initial peer list from loaded state here?

      
   }

   public void startListening() throws IOException {
      try {
         server.start().sync();
      } catch (Exception e) {
         service.fail(e);
      } 
   }

   /**
    * Session factory for our sessions to cluster
    */
   @Override
   public Session makeSession(SocketChannel ch) {
      final Session ses = new WireSession(ch, service);
      ses.setRelayHandler(service);
      ses.setMyEntityId(service.getEntityId());
      ses.setMyEntityType(Core.TYPE_TETRAPOD);
      ses.setTheirEntityType(Core.TYPE_TETRAPOD);
      return ses;
   }

   @Override
   public void onLogEntryApplied(Entry<TetrapodStateMachine> entry) {
      final Command<TetrapodStateMachine> command = entry.getCommand();
      switch (command.getCommandType()) {
         case StateMachine.COMMAND_ID_ADD_PEER:
            addPeer((AddPeerCommand<TetrapodStateMachine>) command);
            break;
         case StateMachine.COMMAND_ID_DEL_PEER:
            // TODO
            break;
      }
   }

   public void bootstrap() {
      logger.info("Bootstrapping new cluster");
      raft.bootstrap(Util.getHostName(), service.getClusterPort());
      service.registerSelf(io.tetrapod.core.registry.Registry.BOOTSTRAP_ID, random.nextLong());
   }

   private void addPeer(AddPeerCommand<TetrapodStateMachine> command) {
      // TODO: Have a think about safety here when loading old log events...
      if (command.peerId != raft.getPeerId()) {
         cluster.put(command.peerId << Registry.PARENT_ID_SHIFT, new TetrapodPeer(service, command.peerId, command.host, command.port));
      }
   }

   public void stop() {
      raft.stop();
   }

   @Override
   public void shutdown() {
      server.stop();
      stop();
   }

   public int getNumSessions() {
      return server.getNumSessions();
   }

   /**
    * Attempts to join the raft cluster by contacting the given node. If we connect, we send a ClusterJoinRequest to obtain a peerId. The
    * peerId is used to derive our entityId, and we expect the raft leader to connect to us and start giving us the state of the system.
    */
   public synchronized boolean joinCluster(final ServerAddress address) {

      // service.registerSelf(peerId << Registry.PARENT_ID_SHIFT, random.nextLong());
      // service.fail("Could not get peerId from " + address);

      final Client client = new Client(this);
      try {
         client.connect(address.host, address.port, service.getDispatcher(), new Session.Listener() {

            @Override
            public void onSessionStop(Session ses) {}

            @Override
            public void onSessionStart(final Session ses) {

               ses.sendRequest(new ClusterJoinRequest(service.getHostName(), service.getServicePort(), service.getClusterPort()),
                     Core.DIRECT).handle(new ResponseHandler() {
                  @Override
                  public void onResponse(Response res) {
                     if (res.isError()) {
                        logger.error("Unable to Join cluster @ {} : {}", address, res.errorCode());
                        ses.close();
                        service.getDispatcher().dispatch(1, TimeUnit.SECONDS, new Runnable() {
                           public void run() {
                              if (!service.isShuttingDown()) {
                                 try {
                                    if (!joinCluster(address)) {
                                       service.fail("Unable to register");
                                    }
                                 } catch (Exception e) {
                                    service.fail("Unable to register: {}" + e);
                                 }
                              }
                           }
                        });
                     } else {
                        ClusterJoinResponse r = (ClusterJoinResponse) res;
                        final int myEntityId = r.peerId << Registry.PARENT_ID_SHIFT;
                        ses.setMyEntityId(myEntityId);
                        ses.setTheirEntityId(r.entityId);
                        service.registerSelf(myEntityId, service.random.nextLong());
                        raft.setPeerId(r.peerId);
                     }
                  }
               });
            }
         }).sync();
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
      }
      return client.isConnected();
   }

   protected Session getSession(int entityId) {
      final TetrapodPeer pod = cluster.get(entityId);
      if (pod != null) {
         return pod.getSession();
      }
      return null;
   }

   private Session getSessionForPeer(int peerId) {
      final TetrapodPeer pod = cluster.get(peerId << Registry.PARENT_ID_SHIFT);
      if (pod != null) {
         return pod.getSession();
      }
      return null;
   }

   private Async sendPeerRequest(Request req, int peerId) {
      Session ses = getSessionForPeer(peerId);
      if (ses != null) {
         return ses.sendRequest(req, Core.DIRECT);
      }
      final Async async = new Async(req, null, null);
      async.setResponse(CoreContract.ERROR_CONNECTION_CLOSED);
      return async;
   }

   public Collection<TetrapodPeer> getMembers() {
      return cluster.values();
   }

   protected void broadcast(Message msg) {
      for (TetrapodPeer pod : getMembers()) {
         if (pod.entityId != service.getEntityId()) {
            if (pod.isConnected()) {
               Session ses = pod.getSession();
               ses.sendMessage(msg, MessageHeader.TO_ENTITY, ses.getTheirEntityId());
            }
         }
      }
   }

   /**
    * Scan our list of known tetrapods and establish a connection to any we are missing
    */
   public void service() {
      if (service.getEntityId() != 0) {
         for (TetrapodPeer pod : cluster.values()) {
            if (pod.entityId != service.getEntityId()) {
               if (pod.getSession() == null) {
                  pod.connect();
               }
            }
         }
      }
   }

   protected void sendClusterDetails(Session ses, int toEntityId, int topicId) {
      // send ourselves
      ses.sendMessage(
            new ClusterMemberMessage(service.getEntityId(), service.getHostName(), service.getServicePort(), service.getClusterPort()),
            MessageHeader.TO_ENTITY, toEntityId);
      // send all current members
      for (TetrapodPeer pod : cluster.values()) {
         ses.sendMessage(new ClusterMemberMessage(pod.entityId, pod.host, pod.servicePort, pod.clusterPort), MessageHeader.TO_ENTITY,
               toEntityId);
      }
   }

   /////////////////////////////////////////////// RAFT RPC REQUEST SENDERS //////////////////////////////////////////////

   @Override
   public void sendRequestVote(String clusterName, int peerId, long term, int candidateId, long lastLogIndex, long lastLogTerm,
         final VoteResponseHandler handler) {

      sendPeerRequest(new VoteRequest(clusterName, term, candidateId, lastLogIndex, lastLogTerm), peerId).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            if (res.isError()) {
               logger.error("VoteRequest {}", res);
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

      sendPeerRequest(new AppendEntriesRequest(term, leaderId, prevLogIndex, prevLogTerm, entryList, leaderCommit), peerId).handle(
            new ResponseHandler() {
               @Override
               public void onResponse(Response res) {
                  if (res.isError()) {
                     logger.error("AppendEntriesRequest {}", res);
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
      sendPeerRequest(new InstallSnapshotRequest(term, index, length, partSize, part, data), peerId).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            if (res.isError()) {
               logger.error("InstallSnapshotRequest {}", res);
            } else {
               InstallSnapshotResponse r = (InstallSnapshotResponse) res;
               handler.handleResponse(r.success);
            }
         }
      });
   }

   // FIXME: This needs to handle errors like invalid leader
   // TODO: Support for idempotent RPCs would be nice
   @Override
   public void sendIssueCommand(int peerId, final Command<TetrapodStateMachine> command,
         final ClientResponseHandler<TetrapodStateMachine> handler) {
      try {
         final byte[] data = commandToBytes(command);
         sendPeerRequest(new IssueCommandRequest(command.getCommandType(), data), peerId).handle(new ResponseHandler() {
            @Override
            public void onResponse(Response res) {
               if (res.isError()) {
                  logger.error("IssueCommandRequest {}", res);
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

   ///////////////////////////////////////////////////// REQUEST HANDLERS //////////////////////////////////////////////////////

   /**
    * A tetrapod has contacted us to join the cluster. We execute a AddPeerCommand to add them, and return them their peerId.
    */
   public Response requestClusterJoin(ClusterJoinRequest req, final RequestContext ctx) {
      final ClientResponseHandler<TetrapodStateMachine> handler = new ClientResponseHandler<TetrapodStateMachine>() {
         @Override
         public void handleResponse(Command<TetrapodStateMachine> command) {
            Response res = Response.error(CoreContract.ERROR_UNKNOWN);
            try {
               if (command != null) {
                  int peerId = ((AddPeerCommand<TetrapodStateMachine>) command).peerId;
                  /// .... FIXME. now what else?
                  res = new ClusterJoinResponse(peerId, service.getEntityId());
               }
            } finally {
               // return the pending result
               ((SessionRequestContext) ctx).session.sendResponse(res, ctx.header.requestId);
            }
         }
      };
      final AddPeerCommand<TetrapodStateMachine> cmd = new AddPeerCommand<>(req.host, req.clusterPort);
      // if we're the leader we can execute directly
      if (!raft.executeCommand(cmd, handler)) {
         // else, send RPC to current leader
         sendIssueCommand(raft.getLeader(), cmd, handler);
      }

      return Response.PENDING;
   }

   @Override
   public Response requestVote(VoteRequest r, RequestContext ctx) {
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

   ///////////////////////////////////// STORAGE API ///////////////////////////////////////

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

   ///////////////////////////////////////////////////////////////////////////////////////////////////////////

   public void logStatus() {
      logger.info(String.format("#%d: %9s term=%d, lastIndex=%d, lastTerm=%d commitIndex=%d, %s, peers=%d", raft.getPeerId(), raft
            .getRole(), raft.getCurrentTerm(), raft.getLog().getLastIndex(), raft.getLog().getLastTerm(), raft.getLog().getCommitIndex(),
            raft.getStateMachine(), raft.getClusterSize()));

      if (raft.getRole() == Role.Leader) {
         // HACK: generate some command activity for testing
         raft.executeCommand(new PutItemCommand<TetrapodStateMachine>("foo", ("bar-" + raft.getLog().getCommitIndex()).getBytes()), null);
      }
   }

   ///////////////////////////////////////////////////////////////////////////////////////////////////////////

}
