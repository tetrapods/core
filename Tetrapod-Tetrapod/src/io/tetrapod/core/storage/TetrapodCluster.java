package io.tetrapod.core.storage;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Meter;

import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.*;
import io.tetrapod.core.pubsub.Publisher;
import io.tetrapod.core.pubsub.Topic;
import io.tetrapod.core.registry.EntityInfo;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.tasks.TaskContext;
import io.tetrapod.core.utils.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.raft.*;
import io.tetrapod.raft.*;
import io.tetrapod.raft.RaftEngine.Role;
import io.tetrapod.raft.storage.*;

/**
 * Wraps a RaftEngine in our Tetrapod-RPC and implements the StorageContract via TetrapodStateMachine
 */
public class TetrapodCluster extends Storage
      implements RaftRPC<TetrapodStateMachine>, RaftContract.API, StateMachine.Listener<TetrapodStateMachine>, SessionFactory {

   private static final Logger                    logger         = LoggerFactory.getLogger(TetrapodCluster.class);

   private final Server                           server;

   /**
    * Maps EntityId to TetrapodPeer
    */
   private final Map<Integer, TetrapodPeer>       cluster        = new ConcurrentHashMap<>();

   private final TetrapodService                  service;

   private final RaftEngine<TetrapodStateMachine> raft;

   private final TetrapodStateMachine             state;

   private final Config                           cfg;

   private final Meter                            commands       = Metrics.meter(this, "commands");
   private final SequentialWorkQueue              queue          = new SequentialWorkQueue();

   /**
    * The index of the command we joined the cluster
    */
   private AtomicLong                             joinIndex      = new AtomicLong(-1);

   /**
    * Maps key prefixes to Topics
    */
   private final Map<Integer, Set<String>>        ownersToTopics = new ConcurrentHashMap<>();

   /**
    * Maps topics to sessions
    */
   private final Map<String, Set<Session>>        topicsToOwners = new ConcurrentHashMap<>();

   public TetrapodCluster(TetrapodService service) {
      this.service = service;

      this.server = new Server(service.getClusterPort(), this, service.getDispatcher());

      // load any system properties passed in
      for (Object key : System.getProperties().keySet()) {
         if (key.toString().startsWith("raft.")) {
            Util.setProperty(key.toString(), System.getProperty(key.toString()));
         }
      }

      this.cfg = new Config().setLogDir(new File(Util.getProperty("raft.logs", "logs/raft")))
            .setClusterName(Util.getProperty("raft.name", "Tetrapod"));

      RaftEngine<TetrapodStateMachine> raftEngine = null;
      try {
         raftEngine = new RaftEngine<TetrapodStateMachine>(cfg, new TetrapodStateMachine.Factory(), this);
      } catch (IOException e) {
         service.fail(e);
      }
      this.raft = raftEngine;
      this.state = this.raft.getStateMachine();
   }

   public void init() throws IOException {
      // add the initial set of entities from the state, then listen for subsequent changes
      synchronized (this.raft) {
         for (EntityInfo info : state.entities.values()) {
            service.registry.onAddEntityCommand(info);
         }
         this.state.addListener(this);
      }

      startListening();
      loadProperties();
   }

   private void queue(Runnable task) {
      queue.queue(task);
   }

   public void loadProperties() {
      int myPeerId = 0;
      int size = Util.getProperty("raft.cluster.size", 1);
      for (int peerId = 1; peerId <= size; peerId++) {
         raft.addPeer(peerId);

         String[] node = Util.getProperty("raft.node." + peerId, Util.getHostName() + ":" + service.getClusterPort()).split(":");
         String host = node[0];
         int port = Integer.parseInt(node[1]);
         int entityId = peerId << TetrapodContract.PARENT_ID_SHIFT;

         if (host.equals(Util.getHostName()) && port == service.getClusterPort()) {
            myPeerId = peerId;
            service.registry.setParentId(entityId);
         }

         addMember(entityId, host, service.getServicePort(), port, null);
      }
      if (myPeerId != 0) {
         service.registerSelf(myPeerId << TetrapodContract.PARENT_ID_SHIFT, service.random.nextLong());
         raft.start(myPeerId);
         service.dispatcher.dispatch(() -> service.checkDependencies());
      } else {
         service.fail("Could not find my peerId for " + Util.getHostName() + ":" + service.getClusterPort());
      }
   }

   public void startListening() throws IOException {
      try {
         server.start().sync();
      } catch (Exception e) {
         raft.stop();
         service.fail(e);
      }
   }

   /**
    * Session factory for our sessions to cluster
    */
   @Override
   public Session makeSession(SocketChannel ch) {
      final Session ses = new WireSession(ch, service);
      ses.setName("Cluster");
      ses.setRelayHandler(service);
      ses.setMyEntityId(service.getEntityId());
      ses.setMyEntityType(Core.TYPE_TETRAPOD);
      ses.setTheirEntityType(Core.TYPE_TETRAPOD);
      return ses;
   }

   public Dispatcher getDispatcher() {
      return service.dispatcher;
   }

   // FIXME: Add a cleaner listener interface based on command type so we don't need a fugly switch
   @Override
   public void onLogEntryApplied(Entry<TetrapodStateMachine> entry) {
      TaskContext taskContext = TaskContext.pushNew();
      try {
         ContextIdGenerator.generate();
         commands.mark();
         final Command<TetrapodStateMachine> command = entry.getCommand();
         switch (command.getCommandType()) {
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
            case AddAdminUserCommand.COMMAND_ID:
               onAddAdminUserCommand((AddAdminUserCommand) command);
               break;
            case DelAdminUserCommand.COMMAND_ID:
               onDelAdminUserCommand((DelAdminUserCommand) command);
               break;
            case ClaimOwnershipCommand.COMMAND_ID:
               onClaimOwnershipCommand((ClaimOwnershipCommand) command);
               break;
            case RetainOwnershipCommand.COMMAND_ID:
               onRetainOwnershipCommand((RetainOwnershipCommand) command);
               break;
            case ReleaseOwnershipCommand.COMMAND_ID:
               onReleaseOwnershipCommand((ReleaseOwnershipCommand) command);
               break;
            case AddEntityCommand.COMMAND_ID:
               onAddEntityCommand((AddEntityCommand) command);
               break;
            case ModEntityCommand.COMMAND_ID:
               onModEntityCommand((ModEntityCommand) command);
               break;
            case DelEntityCommand.COMMAND_ID:
               onDelEntityCommand((DelEntityCommand) command);
               break;
            case RegisterContractCommand.COMMAND_ID:
               onRegisterContractCommand((RegisterContractCommand) command);
               break;
         }
      } finally {
         taskContext.pop();
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

   public Session getSession(int entityId) {
      final TetrapodPeer pod = cluster.get(entityId);
      if (pod != null) {
         return pod.getSession();
      }
      return null;
   }

   private Session getSessionForPeer(int peerId) {
      final TetrapodPeer pod = cluster.get(peerId << TetrapodContract.PARENT_ID_SHIFT);
      if (pod != null) {
         return pod.getSession();
      }
      return null;
   }

   private Async sendPeerRequest(Request req, int peerId) {
      final TaskContext taskContext = TaskContext.pushNew();
      try {
         ContextIdGenerator.generate();
         Session ses = getSessionForPeer(peerId);
         if (ses != null && ses.isConnected()) {
            return ses.sendRequest(req, Core.DIRECT);
         }
         logger.info("Not connected to peer {} ({})", peerId);
         final Async async = new Async(req, null, null);
         async.setResponse(CoreContract.ERROR_CONNECTION_CLOSED);
         return async;
      } finally {
         taskContext.pop();
      }
   }

   public Collection<TetrapodPeer> getMembers() {
      return cluster.values();
   }

   private long lastStatsLog;

   /**
    * Scan our list of known tetrapods and establish a connection to any we are missing
    */
   public void service() {
      queue.process();
      if (service.getEntityId() != 0) {
         for (TetrapodPeer pod : cluster.values()) {
            if (pod.entityId != service.getEntityId()) {
               if (!pod.isConnected()) {
                  pod.connect();
               }
            }
         }

         final long now = System.currentTimeMillis();
         if (now - lastStatsLog > Util.ONE_SECOND * 10) {
            lastStatsLog = System.currentTimeMillis();

            logStatus();

            if (raft.getRole() == Role.Leader) {
               // Generate some command activity periodically to ensure things still moving
               raft.executeCommand(new HealthCheckCommand<TetrapodStateMachine>(), null);
            }

         }

      }
      if (!raft.getLog().isRunning() && !service.isShuttingDown()) {
         service.fail("Raft Log Stopped");
         service.shutdown(false);
      }
   }

   public void sendClusterDetails(Session ses, int toEntityId, int childId, int topicId) {
      // send ourselves
      ses.sendMessage(
            new ClusterMemberMessage(service.getEntityId(), Util.getHostName(), service.getServicePort(), service.getClusterPort(), null),
            toEntityId, childId);
      // send all current members
      for (TetrapodPeer pod : cluster.values()) {
         ses.sendMessage(new ClusterMemberMessage(pod.entityId, pod.host, pod.servicePort, pod.clusterPort, pod.uuid), toEntityId, childId);
      }
      // non-tetrapods need to get some cluster details sent
      if (ses.getTheirEntityType() != Core.TYPE_TETRAPOD) {
         synchronized (raft) {
            // send properties
            for (ClusterProperty prop : state.props.values()) {
               prop = new ClusterProperty(prop.key, prop.secret, prop.val);
               prop.val = AESEncryptor.decryptSaltedAES(prop.val, state.secretKey);
               ses.sendMessage(new ClusterPropertyAddedMessage(prop), toEntityId, childId);
            }
            for (ContractDescription info : state.contracts.values()) {
               ses.sendMessage(new RegisterContractMessage(info), toEntityId, childId);
            }
            for (WebRootDef def : state.webRootDefs.values()) {
               ses.sendMessage(new WebRootAddedMessage(def), toEntityId, childId);
            }
         }
      }
      // tell them they are up to date
      ses.sendMessage(new ClusterSyncedMessage(), toEntityId, childId);
   }

   public void sendAdminDetails(Session ses, int toEntityId, int toChildId, int topicId) {
      if (ses != null) {
         synchronized (raft) {
            // send properties
            for (ClusterProperty prop : state.props.values()) {
               // blank out values for protected properties sent to admins
               prop = new ClusterProperty(prop.key, prop.secret, prop.val);
               prop.val = prop.secret ? "" : AESEncryptor.decryptSaltedAES(prop.val, state.secretKey);
               ses.sendMessage(new ClusterPropertyAddedMessage(prop), toEntityId, toChildId);
            }
            for (WebRootDef def : state.webRootDefs.values()) {
               ses.sendMessage(new WebRootAddedMessage(def), toEntityId, toChildId);
            }
            for (Admin def : state.admins.values()) {
               ses.sendMessage(new AdminUserAddedMessage(def), toEntityId, toChildId);
            }
         }
      }
   }

   public boolean addMember(int entityId, String host, int servicePort, int clusterPort, Session ses) {
      final Entity e = new Entity(entityId, entityId, 0, host, 0, Core.TYPE_TETRAPOD, service.getShortName(), 0, service.getContractId(),
            service.buildName);
      state.addEntity(e, true);
      onAddEntityCommand(new AddEntityCommand(e));

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
      sendPeerRequest(new VoteRequest(clusterName, term, candidateId, lastLogIndex, lastLogTerm), peerId).handle(res -> {
         if (res.isError()) {
            logger.error("VoteRequest {}", res);
         } else {
            VoteResponse r = (VoteResponse) res;
            if (handler != null) {
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

      sendPeerRequest(new AppendEntriesRequest(term, leaderId, prevLogIndex, prevLogTerm, entryList, leaderCommit), peerId).handle(res -> {
         if (res.isError()) {
            if (res.errorCode() == CoreContract.ERROR_CONNECTION_CLOSED) {
               logger.info("AppendEntriesRequest {}", res);
            } else {
               logger.error("AppendEntriesRequest {}", res);
            }
         } else {
            AppendEntriesResponse r = (AppendEntriesResponse) res;
            if (handler != null) {
               handler.handleResponse(r.term, r.success, r.lastLogIndex);
            }
         }
      });
   }

   @Override
   public void sendInstallSnapshot(int peerId, long term, long index, long length, int partSize, int part, byte[] data,
         final InstallSnapshotResponseHandler handler) {
      sendPeerRequest(new InstallSnapshotRequest(term, index, length, partSize, part, data), peerId).handle(res -> {
         if (res.isError()) {
            logger.error("InstallSnapshotRequest {}", res);
         } else {
            InstallSnapshotResponse r = (InstallSnapshotResponse) res;
            if (handler != null) {
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
         sendPeerRequest(new IssueCommandRequest(command.getCommandType(), data), peerId).handle(res -> {
            if (res.isError()) {
               logger.error("IssueCommandRequest {}", res);
               if (handler != null) {
                  handler.handleResponse(null);
               }
            } else {
               if (handler != null) {
                  IssueCommandResponse r = (IssueCommandResponse) res;
                  try {
                     handler.handleResponse(
                           new Entry<TetrapodStateMachine>(r.term, r.index, bytesToCommand(r.command, command.getCommandType())));
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
      Command<TetrapodStateMachine> cmd = state.makeCommandById(type);
      try (ByteArrayInputStream buf = new ByteArrayInputStream(data)) {
         try (DataInputStream in = new DataInputStream(buf)) {
            cmd.read(in, Log.LOG_FILE_VERSION);
            return cmd;
         }
      }
   }

   ///////////////////////////////////////////////////// REQUEST HANDLERS //////////////////////////////////////////////////////

   /**
    * A tetrapod has contacted us to join the cluster.
    */
   public Response requestClusterJoin(final ClusterJoinRequest req, final Topic clusterTopic, final SessionRequestContext ctx) {

      logger.info("**************************** requestClusterJoin {} {}", ctx.session, req.dump());

      final TetrapodPeer peer = cluster.get(req.entityId);
      if (peer == null) {
         return Response.error(CoreContract.ERROR_INVALID_ENTITY);
      }
      peer.servicePort = req.servicePort;

      // register them in our registry
      EntityInfo entity = service.registry.getEntity(req.entityId);
      // reconnecting with a pre-existing peerId
      final int peerId = req.entityId >> TetrapodContract.PARENT_ID_SHIFT;
      if (raft.isValidPeer(peerId)) {
         ctx.session.setTheirEntityId(req.entityId);
         entity.setSession(ctx.session);
         entity.build = req.build;
         entity.status = req.status;

         if (isLeader()) {
            service.registry.updateStatus(entity, entity.status, 0xFFFFFFFF);
         }

         // subscribe them to our cluster and registry views
         logger.info("**************************** SYNC TO {} {}", ctx.session, req.entityId);
         service.subscribeToCluster(ctx.session, req.entityId, 0);

         entity.queue(() -> service.broadcastServicesMessage(new ServiceAddedMessage(entity)));
      }
      return Response.SUCCESS;
   }

   @Override
   public Response requestVote(VoteRequest r, RequestContext ctx) {
      final VoteResponse res = new VoteResponse();
      raft.handleVoteRequest(r.clusterName, r.term, r.candidateId, r.lastLogIndex, r.lastLogTerm, (term, voteGranted) -> {
         res.term = term;
         res.voteGranted = voteGranted;
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
            (term, success, lastLogIndex) -> {
               res.term = term;
               res.success = success;
               res.lastLogIndex = lastLogIndex;
            });
      return res;
   }

   @Override
   public Response requestInstallSnapshot(InstallSnapshotRequest r, RequestContext ctx) {
      final InstallSnapshotResponse res = new InstallSnapshotResponse();
      raft.handleInstallSnapshotRequest(r.term, r.index, r.length, r.partSize, r.part, r.data, success -> res.success = success);
      return res;
   }

   @Override
   public Response requestIssueCommand(IssueCommandRequest r, final RequestContext ctx) {
      try {
         raft.handleClientRequest(bytesToCommand(r.command, r.type), e -> {
            final Session ses = ((SessionRequestContext) ctx).session;
            Response res = Response.error(CoreContract.ERROR_UNKNOWN);
            try {
               if (e != null) {
                  res = new IssueCommandResponse(e.getTerm(), e.getIndex(), commandToBytes(e.getCommand()));
               }
            } catch (IOException ex) {
               logger.error(ex.getMessage(), ex);
            } finally {
               ses.sendResponse(res, ctx.header);
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
      executeCommand(cmd, handler, false);
   }

   public void executeCommand(Command<TetrapodStateMachine> cmd, ClientResponseHandler<TetrapodStateMachine> handler,
         boolean waitForLocal) {
      boolean sent = false;
      while (!sent) {
         // if we're the leader we can execute directly
         if (!raft.executeCommand(cmd, handler)) {
            // else, send RPC to current leader
            if (raft.getLeader() != 0) {

               if (waitForLocal) {
                  final ClientResponseHandler<TetrapodStateMachine> origHandler = handler;
                  handler = entry -> {
                     if (entry != null) {
                        //logger.info("Waiting for local : {} : {}", entry, getCommitIndex());
                        raft.executeAfterCommandProcessed(entry, origHandler);
                     }
                  };

               }
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

   public static abstract class PendingClientResponseHandler<T extends StateMachine<T>> implements ClientResponseHandler<T> {

      public final SessionRequestContext ctx;

      public PendingClientResponseHandler(SessionRequestContext ctx) {
         this.ctx = ctx;
      }

      public abstract Response handlePendingResponse(final Entry<T> entry);

      @Override
      public void handleResponse(final Entry<T> entry) {
         Response res = Response.error(CoreContract.ERROR_UNKNOWN);
         try {
            res = handlePendingResponse(entry);
         } catch (Throwable t) {
            logger.error(t.getMessage(), t);
         } finally {
            ctx.session.sendResponse(res, ctx.header);
         }
      }
   }

   public Response executePendingCommand(final Command<TetrapodStateMachine> cmd,
         final PendingClientResponseHandler<TetrapodStateMachine> handler) {

      // if we're the leader we can execute directly
      if (!raft.executeCommand(cmd, handler)) {
         // else, send RPC to current leader
         if (raft.getLeader() != 0) {
            sendIssueCommand(raft.getLeader(), cmd, handler);
         } else {
            return Response.error(RaftContract.ERROR_NO_LEADER);
         }
      }
      return Response.PENDING;
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
      executeCommand(new IncrementCommand<TetrapodStateMachine>(key), e -> {
         if (e != null) {
            IncrementCommand<TetrapodStateMachine> cmd = (IncrementCommand<TetrapodStateMachine>) e.getCommand();
            val.set(cmd.getResult());
         } else {
            val.set(null);
         }
      });
      return val.waitForValue();
   }

   ///////////////////////////////////////////////////////////////////////////////////////////////////////////

   public void logStatus() {
      logger.info(String.format("#%d: %9s term=%d, lastIndex=%d, lastTerm=%d commitIndex=%d, %s, peers=%d, leader=%d checksum=%016X",
            raft.getPeerId(), raft.getRole(), raft.getCurrentTerm(), raft.getLog().getLastIndex(), raft.getLog().getLastTerm(),
            raft.getLog().getCommitIndex(), state, raft.getClusterSize(), raft.getLeader(), state.getChecksum()));
      if (!raft.getLog().isRunning()) {
         logger.warn("RAFT LOG NOT RUNNING");
      }
   }

   public Response requestRaftStats(RaftStatsRequest r, RequestContext ctx) {
      synchronized (raft) {
         int i = 0;
         int[] peers = new int[raft.getPeers().size()];
         for (RaftEngine.Peer p : raft.getPeers()) {
            peers[i++] = p.peerId << TetrapodContract.PARENT_ID_SHIFT;
         }

         return new RaftStatsResponse((byte) raft.getRole().ordinal(), raft.getCurrentTerm(), raft.getLog().getLastTerm(),
               raft.getLog().getLastIndex(), raft.getLog().getCommitIndex(), raft.getLeader(), peers);
      }
   }

   ///////////////////////////////////////////////////////////////////////////////////////////////////////////

   public void setClusterProperty(ClusterProperty property) {
      property.val = AESEncryptor.encryptSaltedAES(property.val, state.secretKey);
      executeCommand(new SetClusterPropertyCommand(property), null);
   }

   public void delClusterProperty(String key) {
      executeCommand(new DelClusterPropertyCommand(key), null);
   }

   public void registerContract(ContractDescription info) {
      logger.info("Register contract: {} v{}", info.contractId, info.version);
      // FIXME: version isn't updated for minor changes, so we should also include a hash or timestamp for minor updates 
      if (!state.hasContract(info.contractId, info.subContractId, info.version)) {
         executeCommand(new RegisterContractCommand(info), null);
      }
   }

   public WebRoutes getWebRoutes() {
      return state.webRoutes;
   }

   public void setWebRoot(WebRootDef def) {
      executeCommand(new SetWebRouteCommand(def), null);
   }

   public void delWebRoot(String name) {
      executeCommand(new DelWebRouteCommand(name), null);
   }

   private void onSetClusterPropertyCommand(SetClusterPropertyCommand command) {
      final ClusterProperty orig = command.getProperty();

      queue(() -> {
         final ClusterProperty prop = new ClusterProperty(orig.key, orig.secret, orig.val);
         prop.val = AESEncryptor.decryptSaltedAES(prop.val, state.secretKey);
         service.broadcastClusterMessage(new ClusterPropertyAddedMessage(prop));
         if (prop.secret) {
            prop.val = ""; // we don't want to send secret values to admin connections
         }
         service.broadcastAdminMessage(new ClusterPropertyAddedMessage(prop));
      });
   }

   private void onDelClusterPropertyCommand(DelClusterPropertyCommand command) {
      queue(() -> {
         service.broadcastClusterMessage(new ClusterPropertyRemovedMessage(command.getProperty()));
         service.broadcastAdminMessage(new ClusterPropertyRemovedMessage(command.getProperty()));
      });
   }

   private void onSetWebRouteCommand(SetWebRouteCommand command) {
      queue(() -> {
         service.broadcastClusterMessage(new WebRootAddedMessage(command.getWebRouteDef()));
         service.broadcastAdminMessage(new WebRootAddedMessage(command.getWebRouteDef()));
      });
   }

   private void onDelWebRouteCommand(DelWebRouteCommand command) {
      queue(() -> {
         service.broadcastClusterMessage(new WebRootRemovedMessage(command.getWebRouteName()));
         service.broadcastAdminMessage(new WebRootRemovedMessage(command.getWebRouteName()));
      });
   }

   private void onDelAdminUserCommand(DelAdminUserCommand command) {
      queue(() -> {
         service.broadcastAdminMessage(new AdminUserRemovedMessage(command.getAccountId()));
      });
   }

   private void onAddAdminUserCommand(AddAdminUserCommand command) {
      queue(() -> {
         service.broadcastAdminMessage(new AdminUserAddedMessage(command.getAdminUser()));
      });
   }

   public boolean isReady() {
      if (joinIndex.get() > 0) {
         return raft.getStateMachine().getIndex() >= joinIndex.get();
      } else {
         if (joinIndex.get() == -1) {
            joinIndex.set(0);
            executeCommand(new HealthCheckCommand<TetrapodStateMachine>(), entry -> {
               if (entry != null) {
                  joinIndex.set(entry.getIndex());
                  logger.info("Join Index = {}", joinIndex);
               } else {
                  joinIndex.set(-1);
               }
            });
         }
         return false;
      }
   }

   public Admin addAdmin(String email, String hash, long rights) {
      final Value<Admin> val = new Value<Admin>();
      final Admin admin = new Admin(0, email, hash, rights, new long[Admin.MAX_LOGIN_ATTEMPTS]);
      executeCommand(new AddAdminUserCommand(admin), e -> {
         if (e != null) {
            AddAdminUserCommand cmd = (AddAdminUserCommand) e.getCommand();
            val.set(cmd.getAdminUser());
         } else {
            val.set(null);
         }
      });
      return val.waitForValue();
   }

   public Collection<Admin> getAdmins() {
      synchronized (raft) {
         return new ArrayList<Admin>(state.admins.values());
      }
   }

   public Admin getAdmin(int accountId) {
      return state.admins.get(accountId);
   }

   public boolean modify(Admin admin) {
      final Value<Boolean> val = new Value<Boolean>();
      executeCommand(new ModAdminUserCommand(admin), e -> val.set(e != null));
      return val.waitForValue();
   }

   public void snapshot() {
      try {
         raft.getLog().saveSnapshot();
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   public Response requestClaimOwnership(final ClaimOwnershipRequest r, final SessionRequestContext ctx) {
      return executePendingCommand(
            new ClaimOwnershipCommand(ctx.header.fromParentId, r.prefix, r.key, r.leaseMillis, System.currentTimeMillis()),
            new PendingClientResponseHandler<TetrapodStateMachine>(ctx) {
               @Override
               public Response handlePendingResponse(Entry<TetrapodStateMachine> e) {
                  if (e != null) {
                     final ClaimOwnershipCommand c = (ClaimOwnershipCommand) e.getCommand();
                     if (c.wasAcquired()) {
                        return new ClaimOwnershipResponse(ctx.header.fromParentId, c.getExpiry());
                     } else {
                        Owner o = state.ownedItems.get(r.key);
                        if (o != null) {
                           return new ClaimOwnershipResponse(o.entityId, o.expiry);
                        }
                     }
                  }
                  return Response.error(CoreContract.ERROR_UNKNOWN);
               }
            });
   }

   public Response requestRetainOwnership(RetainOwnershipRequest r, final SessionRequestContext ctx) {
      return executePendingCommand(new RetainOwnershipCommand(ctx.header.fromParentId, r.prefix, r.leaseMillis, System.currentTimeMillis()),
            new PendingClientResponseHandler<TetrapodStateMachine>(ctx) {
               @Override
               public Response handlePendingResponse(Entry<TetrapodStateMachine> e) {
                  return e != null ? Response.SUCCESS : Response.error(CoreContract.ERROR_UNKNOWN);
               }
            });
   }

   public Response requestReleaseOwnership(ReleaseOwnershipRequest r, final SessionRequestContext ctx) {
      return executePendingCommand(new ReleaseOwnershipCommand(ctx.header.fromParentId, r.prefix, r.keys),
            new PendingClientResponseHandler<TetrapodStateMachine>(ctx) {
               @Override
               public Response handlePendingResponse(Entry<TetrapodStateMachine> e) {
                  return e != null ? Response.SUCCESS : Response.error(CoreContract.ERROR_UNKNOWN);
               }
            });
   }

   public Response requestSubscribeOwnership(SubscribeOwnershipRequest r, SessionRequestContext ctx) {
      synchronized (topicsToOwners) {
         Set<String> topics = ownersToTopics.get(ctx.header.fromParentId);
         if (topics == null) {
            topics = new HashSet<String>();
            ownersToTopics.put(ctx.header.fromParentId, topics);
         }
         topics.add(r.prefix);

         Set<Session> owners = topicsToOwners.get(r.prefix);
         if (owners == null) {
            owners = new HashSet<Session>();
            topicsToOwners.put(r.prefix, owners);
         }
         owners.add(ctx.session);

         for (Owner owner : state.owners.values()) {
            for (String key : owner.keys) {
               if (key.startsWith(r.prefix)) {
                  ctx.session.sendMessage(new ClaimOwnershipMessage(owner.entityId, owner.expiry, key), ctx.header.fromParentId, 0);
               }
            }
         }
      }

      return Response.SUCCESS;
   }

   public Response requestUnsubscribeOwnership(UnsubscribeOwnershipRequest r, RequestContext ctx) {
      unsubscribeOwnership(ctx.header.fromParentId);
      return Response.SUCCESS;
   }

   private void unsubscribeOwnership(int entityId) {
      synchronized (topicsToOwners) {
         final Set<String> topics = ownersToTopics.remove(entityId);
         if (topics != null) {
            for (String topic : topics) {
               topicsToOwners.get(topic).remove(entityId);
            }
         }
      }
   }

   private void onReleaseOwnershipCommand(ReleaseOwnershipCommand command) {
      final Message msg = new ReleaseOwnershipMessage(command.getOwnerId(), command.getPrefix(), command.getKeys());

      synchronized (topicsToOwners) {
         final Set<Session> owners = topicsToOwners.get(command.getPrefix());
         if (owners != null) {
            for (Session ses : owners) {
               sendOwnershipMessage(msg, ses);
            }
         }
      }
   }

   private void onRetainOwnershipCommand(RetainOwnershipCommand command) {
      final Message msg = new RetainOwnershipMessage(command.getOwnerId(), command.getPrefix(), command.getExpiry());
      synchronized (topicsToOwners) {
         final Set<Session> owners = topicsToOwners.get(command.getPrefix());
         if (owners != null) {
            for (Session ses : owners) {
               sendOwnershipMessage(msg, ses);
            }
         }
      }

   }

   private void onClaimOwnershipCommand(ClaimOwnershipCommand command) {
      if (command.wasAcquired()) {
         logger.info("**** CLAIMED : {} : {} ", command.getOwnerId(), command.getKey());

         final String key = command.getKey();
         final Message msg = new ClaimOwnershipMessage(command.getOwnerId(), command.getExpiry(), key);
         synchronized (topicsToOwners) {
            final Set<Session> owners = topicsToOwners.get(command.getPrefix());
            if (owners != null) {
               for (Session ses : owners) {
                  sendOwnershipMessage(msg, ses);
               }
            }
         }
      }
   }

   private void sendOwnershipMessage(Message msg, Session ses) {
      if (ses.isConnected()) {
         ses.sendMessage(msg, ses.getTheirEntityId(), 0);
      } else {
         unsubscribeOwnership(ses.getTheirEntityId());
      }
   }

   public EntityInfo getEntity(int entityId) {
      return state.entities.get(entityId);
   }

   public Collection<EntityInfo> getEntities() {
      return state.entities.values();
   }

   private void onAddEntityCommand(AddEntityCommand command) {
      service.registry.onAddEntityCommand(state.entities.get(command.getEntity().entityId));
   }

   private void onModEntityCommand(ModEntityCommand command) {
      service.registry.onModEntityCommand(state.entities.get(command.getEntityId()));
   }

   private void onDelEntityCommand(DelEntityCommand command) {
      service.registry.onDelEntityCommand(command.getEntityId());
   }

   private void onRegisterContractCommand(RegisterContractCommand command) {
      queue(() -> {
         service.broadcastClusterMessage(new RegisterContractMessage(command.getContractDescription()));
      });
   }

   public long getCommitIndex() {
      return raft.getLog().getCommitIndex();
   }

   public boolean isValidPeer(int entityId) {
      final int peerId = entityId >> TetrapodContract.PARENT_ID_SHIFT;
      return peerId == raft.getPeerId() || raft.isValidPeer(peerId);
   }

   public void logRegistry() {
      List<EntityInfo> list = new ArrayList<>(getEntities());
      Collections.sort(list);
      logger.info("=========================@ TETRAPOD CLUSTER REGISTRY @===========================");
      for (EntityInfo e : list) {
         logger.info(String.format(" 0x%08X 0x%08X %-15s status=%08X [%s]", e.parentId, e.entityId, e.name, e.status,
               e.hasConnectedSession() ? "CONNECTED" : "*"));
      }
      logger.info("=================================================================================\n");
   }

   public boolean isLeader() {
      synchronized (raft) {
         return raft.getRole() == Role.Leader;
      }
   }

   public Publisher getPublisher() {
      return service.getPublisher();
   }

   public ServerAddress getLeader() {
      synchronized (raft) {
         final int peerId = raft.getLeader();
         for (TetrapodPeer p : cluster.values()) {
            if (peerId == p.peerId) {
               return new ServerAddress(p.host, p.servicePort);
            }
         }
      }
      return null;
   }

}
