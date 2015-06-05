package io.tetrapod.core.storage;

import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.*;
import io.tetrapod.core.registry.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.*;
import io.tetrapod.core.web.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.raft.*;
import io.tetrapod.raft.*;
import io.tetrapod.raft.RaftEngine.Role;
import io.tetrapod.raft.StateMachine.Peer;
import io.tetrapod.raft.storage.*;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.*;

/**
 * Wraps a RaftEngine in our Tetrapod-RPC and implements the StorageContract via TetrapodStateMachine
 * 
 * Cluster Joining Steps:
 * <ol>
 * <li>Connect to an existing member
 * <li>Ask for a tetrapod peerId
 * <li>For each known member send ClusterJoinRequest to subscribes to that member's registry, discover more peers
 * </ol>
 */
public class TetrapodCluster extends Storage implements RaftRPC<TetrapodStateMachine>, RaftContract.API,
      StateMachine.Listener<TetrapodStateMachine>, SessionFactory {

   private static final Logger                    logger    = LoggerFactory.getLogger(TetrapodCluster.class);

   private final SecureRandom                     random    = new SecureRandom();

   private final Server                           server;

   /**
    * Maps EntityId to TetrapodPeer
    */
   private final Map<Integer, TetrapodPeer>       cluster   = new ConcurrentHashMap<>();

   private final TetrapodService                  service;

   private final RaftEngine<TetrapodStateMachine> raft;

   private final TetrapodStateMachine             state;

   private final Config                           cfg;

   /**
    * The index of the command we joined the cluster
    */
   private AtomicLong                             joinIndex = new AtomicLong(-1);

   public TetrapodCluster(TetrapodService service) {
      this.service = service;

      this.server = new Server(service.getClusterPort(), this, service.getDispatcher());

      this.cfg = new Config().setLogDir(new File(Util.getProperty("raft.logs", "logs/raft"))).setClusterName(
            Util.getProperty("raft.name", "Tetrapod"));

      RaftEngine<TetrapodStateMachine> raftEngine = null;
      try {
         raftEngine = new RaftEngine<TetrapodStateMachine>(cfg, new TetrapodStateMachine.Factory(), this);
      } catch (IOException e) {
         service.fail(e);
      }
      this.raft = raftEngine;
      this.state = this.raft.getStateMachine();
      this.state.addListener(this);

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
            delPeer((DelPeerCommand<TetrapodStateMachine>) command, entry);
            break;
         case SetClusterPropertyCommand.COMMAND_ID:
            onSetClusterPropertyCommand((SetClusterPropertyCommand) command);
            break;
         case DelClusterPropertyCommand.COMMAND_ID:
            onDelClusterPropertyCommand((DelClusterPropertyCommand) command);
            break;
         case SetWebRouteCommand.COMMAND_ID:
            onSetWebRouteCommand((SetWebRouteCommand) command);
            break;
         case DelWebRouteCommand.COMMAND_ID:
            onDelWebRouteCommand((DelWebRouteCommand) command);
            break;
      }
   }

   public void bootstrap() {
      logger.info("Bootstrapping new cluster");
      raft.bootstrap(Util.getHostName(), service.getClusterPort());
      service.registerSelf(io.tetrapod.core.registry.Registry.BOOTSTRAP_ID, random.nextLong());
      //    joinIndex = raft.getLog().getLastIndex();
      service.checkDependencies();

   }

   private void addPeer(AddPeerCommand<TetrapodStateMachine> command) {}

   private void delPeer(DelPeerCommand<TetrapodStateMachine> command, Entry<TetrapodStateMachine> entry) {
      if (command.peerId == raft.getPeerId() && joinIndex.get() > 0 && entry.getIndex() > joinIndex.get()) {
         service.shutdown(false);
      }
   }

   public void stop() {
      //      if (raft.getRole() != Role.Leaving) {
      //         executeCommand(new DelPeerCommand<TetrapodStateMachine>(raft.getPeerId()), null);
      //      }
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
    * Looks through raft state for peers we can attempt to join
    */
   public synchronized boolean joinCluster() {
      for (Peer peer : state.getPeers()) {
         if (!(peer.port == service.getClusterPort() && peer.host.equals(Util.getHostName()))) {
            if (joinCluster(new ServerAddress(peer.host, peer.port))) {
               return true;
            }
         }
      }
      return false;
   }

   /**
    * Attempts to join the raft cluster by contacting the given node. If we connect, we send a ClusterJoinRequest to obtain a peerId. The
    * peerId is used to derive our entityId, and we expect the raft leader to start giving us the state of the system.
    */
   public synchronized boolean joinCluster(final ServerAddress address) {

      final Client client = new Client(this);
      try {
         client.connect(address.host, address.port, service.getDispatcher(), new Session.Listener() {

            @Override
            public void onSessionStop(Session ses) {}

            @Override
            public void onSessionStart(final Session ses) {

               ses.sendRequest(new IssuePeerIdRequest(Util.getHostName(), service.getClusterPort()), Core.DIRECT).handle(
                     new ResponseHandler() {
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
                              final IssuePeerIdResponse r = (IssuePeerIdResponse) res;
                              final int myEntityId = r.peerId << Registry.PARENT_ID_SHIFT;
                              ses.setMyEntityId(myEntityId);
                              ses.setTheirEntityId(r.entityId);
                              // ses.close();

                              service.registerSelf(myEntityId, service.random.nextLong());
                              raft.start(r.peerId);

                              // HACK: We need to wait for our loop-back connection to be established
                              service.dispatcher.dispatch(1, TimeUnit.SECONDS, new Runnable() {
                                 public void run() {
                                    addMember(r.entityId, address.host, Core.DEFAULT_SERVICE_PORT, address.port, ses);
                                    service.checkDependencies();
                                 }
                              });
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

   public Session getSession(int entityId) {
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

   public void broadcast(Message msg) {
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
               if (!pod.isConnected()) {
                  pod.connect();
               }
            }
         }
      }
   }

   public void sendClusterDetails(Session ses, int toEntityId, int topicId) {
      // send ourselves
      ses.sendMessage(
            new ClusterMemberMessage(service.getEntityId(), Util.getHostName(), service.getServicePort(), service.getClusterPort(), null),
            MessageHeader.TO_ENTITY, toEntityId);
      // send all current members
      for (TetrapodPeer pod : cluster.values()) {
         ses.sendMessage(new ClusterMemberMessage(pod.entityId, pod.host, pod.servicePort, pod.clusterPort, pod.uuid),
               MessageHeader.TO_ENTITY, toEntityId);
      }
      // non-tetrapods need to get some cluster details sent
      if (ses.getTheirEntityType() != Core.TYPE_TETRAPOD) {
         synchronized (raft) {
            // send properties
            for (ClusterProperty prop : state.props.values()) {
               if (ses.getTheirEntityType() == Core.TYPE_ADMIN) {
                  // blank out values for protected properties sent to admins
                  prop = new ClusterProperty(prop.key, prop.secret, prop.secret ? "" : prop.val);
               }
               ses.sendMessage(new ClusterPropertyAddedMessage(prop), MessageHeader.TO_ENTITY, toEntityId);
            }
            // admin app needs web roots
            if (ses.getTheirEntityType() == Core.TYPE_ADMIN) {
               for (WebRootDef def : state.webRootDefs.values()) {
                  ses.sendMessage(new WebRootAddedMessage(def), MessageHeader.TO_ENTITY, toEntityId);
               }
            }
         }
      }
      // tell them they are up to date
      ses.sendMessage(new ClusterSyncedMessage(), MessageHeader.TO_ENTITY, toEntityId);
   }

   public boolean addMember(int entityId, String host, int servicePort, int clusterPort, Session ses) {
      // ignore ourselves
      if (entityId == service.getEntityId()) {
         return false;
      }

      TetrapodPeer pod = cluster.get(entityId);
      if (pod != null) {
         pod.servicePort = servicePort;
         if (pod.isConnected()) {
            return false;
         }
      } else {
         pod = new TetrapodPeer(service, entityId, host, clusterPort, servicePort);
         cluster.put(entityId, pod);
         logger.info(" * ADDING TETRAPOD CLUSTER MEMBER: {} @ {}", pod, ses);
      }

      if (ses != null) {
         pod.setSession(ses);
      }
      return true;
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
                  handler.handleResponse(null);
               } else {
                  if (handler != null) {
                     IssueCommandResponse r = (IssueCommandResponse) res;
                     try {
                        handler.handleResponse(new Entry<TetrapodStateMachine>(r.term, r.index, bytesToCommand(r.command,
                              command.getCommandType())));
                     } catch (IOException e) {
                        logger.error(e.getMessage(), e);
                     }
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
      Command<TetrapodStateMachine> cmd = (Command<TetrapodStateMachine>) state.makeCommandById(type);
      try (ByteArrayInputStream buf = new ByteArrayInputStream(data)) {
         try (DataInputStream in = new DataInputStream(buf)) {
            cmd.read(in);
            return cmd;
         }
      }
   }

   ///////////////////////////////////////////////////// REQUEST HANDLERS //////////////////////////////////////////////////////

   /**
    * A new tetrapod is asking to be issued a unique peerId for the cluster. Find the leader and issue a command to generate the next
    * available peerId
    */
   public Response requestIssuePeerId(final IssuePeerIdRequest r, final SessionRequestContext ctx) {
      for (TetrapodPeer peer : cluster.values()) {
         if (peer.host.equals(r.host) && peer.clusterPort == r.clusterPort) {

            EntityInfo e = service.registry.getEntity(peer.entityId);
            if (e != null) {
               service.registry.clearGone(e, ctx.session);
            }
            peer.setSession(ctx.session);

            return new IssuePeerIdResponse(peer.peerId, service.getEntityId(), raft.getLog().getLastTerm(), raft.getLog().getLastIndex());
         }
      }

      final ClientResponseHandler<TetrapodStateMachine> handler = new ClientResponseHandler<TetrapodStateMachine>() {
         @Override
         public void handleResponse(Entry<TetrapodStateMachine> e) {
            Response res = Response.error(CoreContract.ERROR_UNKNOWN);
            try {
               if (e != null) {
                  final int peerId = ((AddPeerCommand<TetrapodStateMachine>) e.getCommand()).peerId;
                  res = new IssuePeerIdResponse(peerId, service.getEntityId(), e.getTerm(), e.getIndex());
               }
            } finally {
               // return the pending result
               ctx.session.sendResponse(res, ctx.header.requestId);
            }
         }
      };
      final AddPeerCommand<TetrapodStateMachine> cmd = new AddPeerCommand<>(r.host, r.clusterPort);
      // if we're the leader we can execute directly
      if (!raft.executeCommand(cmd, handler)) {
         // else, send RPC to current leader
         sendIssueCommand(raft.getLeader(), cmd, handler);
      }

      return Response.PENDING;
   }

   /**
    * A tetrapod has contacted us to join the cluster.
    */
   public Response requestClusterJoin(final ClusterJoinRequest req, final Topic clusterTopic, final SessionRequestContext ctx) {

      logger.info("**************************** requestClusterJoin {} {}", ctx.session, req.dump());

      ctx.session.setTheirEntityId(req.entityId);

      // register them in our registry
      EntityInfo entity = service.registry.getEntity(req.entityId);
      if (entity == null) {
         entity = new EntityInfo(req.entityId, req.entityId, 0L, req.host, req.status, Core.TYPE_TETRAPOD, "Tetrapod*", req.build,
               TetrapodContract.VERSION, TetrapodContract.CONTRACT_ID);
         service.registry.register(entity);
      }
      entity.setSession(ctx.session);

      // add them to the cluster list
      if (addMember(req.entityId, req.host, req.servicePort, req.clusterPort, ctx.session)) {
         service.broadcast(new ClusterMemberMessage(req.entityId, req.host, req.servicePort, req.clusterPort, null), clusterTopic);
      }

      // subscribe them to our cluster and registry views
      logger.info("**************************** SYNC TO {} {}", ctx.session, req.entityId);

      service.registrySubscribe(ctx.session, req.entityId, true);
      service.subscribeToCluster(ctx.session, req.entityId);

      return Response.SUCCESS;
   }

   /**
    * Asks this tetrapod to try and leave the cluster.
    */
   public Response requestClusterLeave(final ClusterLeaveRequest req, final SessionRequestContext ctx) {
      if (raft.getPeerId() != 0) {
         int peerId = req.entityId >> Registry.PARENT_ID_SHIFT;
         executeCommand(new DelPeerCommand<TetrapodStateMachine>(peerId), new ClientResponseHandler<TetrapodStateMachine>() {
            @Override
            public void handleResponse(Entry<TetrapodStateMachine> e) {
               if (e != null) {
                  // on success we can shutdown
                  ctx.session.sendResponse(Response.SUCCESS, ctx.header.requestId);
               } else {
                  ctx.session.sendResponse(Response.error(CoreContract.ERROR_UNKNOWN), ctx.header.requestId);
               }
            }
         });
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
   public Response requestIssueCommand(IssueCommandRequest r, final RequestContext ctx) {
      try {
         raft.handleClientRequest(bytesToCommand(r.command, r.type), new ClientResponseHandler<TetrapodStateMachine>() {
            @Override
            public void handleResponse(Entry<TetrapodStateMachine> e) {
               final Session ses = ((SessionRequestContext) ctx).session;
               Response res = Response.error(CoreContract.ERROR_UNKNOWN);
               try {
                  if (e != null) {
                     res = new IssueCommandResponse(e.getTerm(), e.getIndex(), commandToBytes(e.getCommand()));
                  }
               } catch (IOException ex) {
                  logger.error(ex.getMessage(), ex);
               } finally {
                  ses.sendResponse(res, ctx.header.requestId);
               }
            }
         });
         return Response.PENDING;
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public Response genericRequest(Request r, RequestContext ctx) {
      return null;
   }

   ///////////////////////////////////// STORAGE API ///////////////////////////////////////

   public void executeCommand(Command<TetrapodStateMachine> cmd, ClientResponseHandler<TetrapodStateMachine> handler) {
      boolean sent = false;
      while (!sent) {
         // if we're the leader we can execute directly
         if (!raft.executeCommand(cmd, handler)) {
            // else, send RPC to current leader
            if (raft.getLeader() != 0) {
               sendIssueCommand(raft.getLeader(), cmd, handler);
               sent = true; // FIXME
            } else {
               Util.sleep(10);
            }
         } else {
            sent = true;
         }
      }
   }

   @Override
   public void put(String key, String value) {
      executeCommand(new PutItemCommand<TetrapodStateMachine>(key, value), null);
   }

   @Override
   public void delete(String key) {
      executeCommand(new RemoveItemCommand<TetrapodStateMachine>(key), null);
   }

   public enum ReadType {
      /**
       * Read from local state (Eventually Consistent read)
       */
      Local,

      /**
       * Read from leader via RPC (Optimistic read)
       */
      Leader,

      /**
       * Read via command (Strongly consistent read)
       */
      Command
   }

   public String get(String key, ReadType readType) {
      switch (readType) {
         case Command:
            // TODO / FIXME / IMPLEMENT
            //executeCommand(new GetItemCommand<TetrapodStateMachine>(key), null);
         case Leader:
            // TODO / FIXME / IMPLEMENT
            // send RPC to read from leader
         case Local:
            StorageItem item = state.getItem(key);
            return item != null ? item.getDataAsString() : null;
      }
      return null;
   }

   @Override
   public String get(String key) {
      return get(key, ReadType.Leader);
   }

   @Override
   public DistributedLock getLock(String lockKey) {
      return new DistributedLock(lockKey, this);
   }

   @Override
   public long increment(String key) {
      final Value<Long> val = new Value<Long>();
      executeCommand(new IncrementCommand<TetrapodStateMachine>(key), new ClientResponseHandler<TetrapodStateMachine>() {
         @Override
         public void handleResponse(Entry<TetrapodStateMachine> e) {
            if (e != null) {
               IncrementCommand<TetrapodStateMachine> cmd = (IncrementCommand<TetrapodStateMachine>) e.getCommand();
               val.set(cmd.getResult());
            } else {
               val.set(null);
            }
         }
      });
      return val.waitForValue();
   }

   ///////////////////////////////////////////////////////////////////////////////////////////////////////////

   public void logStatus() {
      logger.info(String.format("#%d: %9s term=%d, lastIndex=%d, lastTerm=%d commitIndex=%d, %s, peers=%d, leader=%d checksum=%016X", raft
            .getPeerId(), raft.getRole(), raft.getCurrentTerm(), raft.getLog().getLastIndex(), raft.getLog().getLastTerm(), raft.getLog()
            .getCommitIndex(), state, raft.getClusterSize(), raft.getLeader(), state.getChecksum()));

      if (raft.getRole() == Role.Leader) {
         // Generate some command activity periodically to ensure things still moving
         raft.executeCommand(new HealthCheckCommand<TetrapodStateMachine>(), null);
      }
   }

   public Response requestRaftStats(RaftStatsRequest r, RequestContext ctx) {
      synchronized (raft) {
         int i = 0;
         int[] peers = new int[state.getPeers().size()];
         for (Peer p : state.getPeers()) {
            peers[i++] = p.peerId << Registry.PARENT_ID_SHIFT;
         }

         return new RaftStatsResponse((byte) raft.getRole().ordinal(), raft.getCurrentTerm(), raft.getLog().getLastTerm(), raft.getLog()
               .getLastIndex(), raft.getLog().getCommitIndex(), raft.getLeader(), peers);
      }
   }

   ///////////////////////////////////////////////////////////////////////////////////////////////////////////

   public void setClusterProperty(ClusterProperty property) {
      executeCommand(new SetClusterPropertyCommand(property), null);
   }

   public void delClusterProperty(String key) {
      executeCommand(new DelClusterPropertyCommand(key), null);
   }

   public void registerContract(ContractDescription info) {
      // FIXME: version isn't updated for minor changes, so we should also include a hash or timestamp for minor updates 
      if (state.hasContract(info.contractId, info.version)) {
         executeCommand(new RegisterContractCommand(info), null);
      }
   }

   private void onSetClusterPropertyCommand(SetClusterPropertyCommand command) {
      // FIXME: Security hole here, when a secret property is sent to all subscribers here,
      // we don't want to send the value to admin connections, just service connections
      service.broadcastClusterMessage(new ClusterPropertyAddedMessage(command.getProperty()));
   }

   private void onDelClusterPropertyCommand(DelClusterPropertyCommand command) {
      service.broadcastClusterMessage(new ClusterPropertyRemovedMessage(command.getProperty()));
   }

   public WebRoutes getWebRoutes() {
      return state.webRoutes;
   }

   public Map<String, WebRoot> getWebRootDirs() {
      return state.webRootDirs;
   }

   public void setWebRoot(WebRootDef def) {
      executeCommand(new SetWebRouteCommand(def), null);
   }

   public void delWebRoot(String name) {
      executeCommand(new DelWebRouteCommand(name), null);
   }

   private void onSetWebRouteCommand(SetWebRouteCommand command) {
      service.broadcastClusterMessage(new WebRootAddedMessage(command.getWebRouteDef()));
   }

   private void onDelWebRouteCommand(DelWebRouteCommand command) {
      service.broadcastClusterMessage(new WebRootRemovedMessage(command.getWebRouteName()));
   }

   public boolean isReady() {
      if (joinIndex.get() > 0) {
         return raft.getStateMachine().getIndex() >= joinIndex.get();
      } else {
         if (joinIndex.get() == -1) {
            joinIndex.set(0);
            executeCommand(new HealthCheckCommand<TetrapodStateMachine>(), new ClientResponseHandler<TetrapodStateMachine>() {
               @Override
               public void handleResponse(Entry<TetrapodStateMachine> entry) {
                  if (entry != null) {
                     joinIndex.set(entry.getIndex());
                     logger.info("Join Index = {}", joinIndex);
                  } else {
                     joinIndex.set(-1);
                  }
               }
            });
         }
         return false;
      }

   }

}
