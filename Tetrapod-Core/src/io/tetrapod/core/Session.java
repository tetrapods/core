package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.*;
import static io.tetrapod.protocol.core.CoreContract.*;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import javax.net.ssl.SSLException;

import org.slf4j.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;
import io.tetrapod.core.logging.CommsLogger;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.tasks.Task;
import io.tetrapod.protocol.core.*;

/**
 * Manages a tetrapod session
 */
abstract public class Session extends ChannelInboundHandlerAdapter {

   /**
    * Listeners of session life-cycle events
    */
   public interface Listener {

      public void onSessionStart(Session ses);

      public void onSessionStop(Session ses);
   }

   public static interface Helper {

      public Dispatcher getDispatcher();

      public <TResp extends Response> Async dispatchRequest(RequestHeader header, Request req, Session fromSession);

      public List<SubscriptionAPI> getMessageHandlers(int contractId, int structId);

      public int getContractId();
   }

   public static interface RelayHandler {

      public int getAvailableService(int contractid);

      public Session getRelaySession(int toId, int contractid);

      public void relayMessage(MessageHeader header, ByteBuf buf, boolean isBroadcast) throws IOException;

      public WebRoutes getWebRoutes();

   }

   protected static final Logger        logger                     = LoggerFactory.getLogger(Session.class);
   protected static final Logger        commsLog                   = LoggerFactory.getLogger("comms");

   public static final byte             DEFAULT_REQUEST_TIMEOUT    = 30;
   public static final int              DEFAULT_OVERLOAD_THRESHOLD = 1024 * 128;

   protected static final AtomicInteger sessionCounter             = new AtomicInteger();

   protected final int                  sessionNum                 = sessionCounter.incrementAndGet();

   protected final List<Listener>       listeners                  = new LinkedList<Listener>();
   protected final Map<Integer, Async>  pendingRequests            = new ConcurrentHashMap<>();
   protected final AtomicInteger        requestCounter             = new AtomicInteger();
   protected final Session.Helper       helper;
   protected final SocketChannel        channel;
   protected final AtomicLong           lastHeardFrom              = new AtomicLong(System.currentTimeMillis());
   protected final AtomicLong           lastSentTo                 = new AtomicLong(System.currentTimeMillis());

   protected RelayHandler               relayHandler;

   protected String                     name;

   protected int                        myId                       = 0;
   protected byte                       myType                     = Core.TYPE_ANONYMOUS;

   protected int                        theirId                    = 0;
   protected byte                       theirType                  = Core.TYPE_ANONYMOUS;

   protected int                        myContractId;

   public Session(SocketChannel channel, Session.Helper helper) {
      this.channel = channel;
      this.helper = helper;
      this.myContractId = helper.getContractId();
   }

   public synchronized void setName(String name) {
      this.name = name;
   }

   public synchronized String getName() {
      return name;
   }

   /**
    * Check to see if this session is still alive and close it, if not
    */
   // TODO: needs configurable timeouts
   public void checkHealth() {
      if (isConnected()) {
         final long now = System.currentTimeMillis();
         if (now - lastHeardFrom.get() > 5000 || now - lastSentTo.get() > 5000) {
            if (theirType == TYPE_SERVICE || theirType == TYPE_TETRAPOD)
               logger.trace("{} Sending PING ({}/{} ms)", this, now - lastHeardFrom.get(), now - lastSentTo.get());
            sendPing();
         }
         if (now - lastHeardFrom.get() > 20000) {
            logger.warn("{} Timeout ({} ms)", this, now - lastHeardFrom.get());
            if (theirId == myId && myId != 0) {
               logger.error("{} Timeout on LOOPBACK!", this);
            } else {
               close();
            }
         }
      }
      timeoutPendingRequests();
   }

   public void timeoutPendingRequests() {
      for (Entry<Integer, Async> entry : pendingRequests.entrySet()) {
         Async a = entry.getValue();
         if (a.isTimedout()) {
            pendingRequests.remove(entry.getKey());
            a.setResponse(ERROR_TIMEOUT);
         }
      }
   }

   abstract protected Object makeFrame(Structure header, Structure payload, byte envelope);

   abstract protected Object makeFrame(Structure header, ByteBuf payload, byte envelope);

   protected void sendPing() {}

   protected void sendPong() {}

   public Dispatcher getDispatcher() {
      return helper.getDispatcher();
   }

   public int getSessionNum() {
      return sessionNum;
   }

   public long getLastHeardFrom() {
      return lastHeardFrom.get();
   }

   protected void scheduleHealthCheck() {
      if (isConnected() || !pendingRequests.isEmpty()) {
         getDispatcher().dispatch(1, TimeUnit.SECONDS, () -> {
            checkHealth();
            scheduleHealthCheck();
         });
      }
   }

   @Override
   public String toString() {
      return String.format("%s%s #%d [0x%08X]", getClass().getSimpleName(), name == null ? "" : name, sessionNum, theirId);
   }

   protected String getStructName(int contractId, int structId) {
      Structure s = StructureFactory.make(contractId, structId);
      if (s == null) {
         return "Struct-" + structId;
      }
      return s.getClass().getSimpleName();
   }

   protected void dispatchRequest(final RequestHeader header, final Request req) {
      helper.dispatchRequest(header, req, this).handle(res -> sendResponse(res, header));
   }

   public void dispatchMessage(final MessageHeader header, final Message msg) {
      // we need  to hijack this now to prevent a race with dispatching subsequent messages
      if (header.structId == EntityMessage.STRUCT_ID) {
         if (getTheirEntityType() == Core.TYPE_TETRAPOD) {
            EntityMessage m = (EntityMessage) msg;
            setMyEntityId(m.entityId);
         }
      }

      // OPTIMIZE: use senderId to queue instead of using this single threaded queue
      final MessageContext ctx = new SessionMessageContext(header, this);
      getDispatcher().dispatchSequential(() -> {
         for (SubscriptionAPI handler : helper.getMessageHandlers(header.contractId, header.structId)) {
            msg.dispatch(handler, ctx);
         }
      });
   }

   public Response sendPendingRequest(final Request req, final int toId, byte timeoutSeconds, final PendingResponseHandler pendingHandler) {
      final Async async = sendRequest(req, toId, timeoutSeconds);
      async.handle(res -> {
         Response pendingRes = null;
         try {
            pendingRes = pendingHandler.onResponse(res);
         } catch (ErrorResponseException e1) {
            pendingRes = Response.error(e1.errorCode);
         } catch (Throwable e) {
            logger.error(e.getMessage(), e);
         } finally {
            if (pendingRes != Response.PENDING) {
               // finally return the pending response we were waiting on
               if (pendingRes == null) {
                  pendingRes = new Error(ERROR_UNKNOWN);
               }
               if (!pendingHandler.sendResponse(pendingRes)) {
                  sendResponse(pendingRes, pendingHandler.context.header);
               }
            } else {
               logger.debug("Pending response returned from pending handler for {} @ {}", req, toId);
            }
         }
      });
      return Response.PENDING;
   }

   public Async sendRequest(Request req, int toId) {
      return sendRequest(req, toId, DEFAULT_REQUEST_TIMEOUT);
   }

   public Task<? extends Response> sendRequestTask(Request req, int toId, byte timeoutSeconds) {
      Task<Response> task = new Task<>();
      Async async = sendRequest(req, toId, timeoutSeconds);
      async.handle(resp -> {
         if (resp.isError() && resp.errorCode() == ERROR_UNKNOWN) {
            task.completeExceptionally(new ErrorResponseException(resp.errorCode()));
         } else {
            task.complete(resp);
         }
      });
      return task;
   }

   public Async sendRequest(Request req, int toId, byte timeoutSeconds) {
      final RequestHeader header = new RequestHeader();
      header.requestId = requestCounter.incrementAndGet();
      header.toId = toId;
      header.fromParentId = myId;
      header.fromChildId = 0;
      header.timeout = timeoutSeconds;
      header.contractId = req.getContractId();
      header.structId = req.getStructId();
      header.fromType = myType;
      header.contextId = ContextIdGenerator.getContextId();
      return sendRequest(req, header);
   }

   private Async sendRequest(Request req, final RequestHeader header) {
      final Async async = new Async(req, header, this);
      if (channel.isActive()) {
         pendingRequests.put(header.requestId, async);

         CommsLogger.append(this, true, header, req);
         //         if (!CommsLogger.commsLogIgnore(req))
         //            commsLog("%s %016X [%d] => %s (to %d)", this, header.contextId, header.requestId, req.dump(), header.toId);

         final Object buffer = makeFrame(header, req, ENVELOPE_REQUEST);
         if (buffer != null) {
            writeFrame(buffer);
         } else {
            async.setResponse(ERROR_SERIALIZATION);
         }
      } else {
         async.setResponse(ERROR_CONNECTION_CLOSED);
      }
      return async;
   }

   public void sendResponse(Response res, RequestHeader reqHeader) {
      if (res != Response.PENDING) {
         final ResponseHeader header = new ResponseHeader(reqHeader.requestId, res.getContractId(), res.getStructId(), reqHeader.contextId);
         CommsLogger.append(this, true, header, res, reqHeader.structId);
         //         if (!CommsLogger.commsLogIgnore(res))
         //            commsLog("%s %016X [%d] => %s", this, contextId, requestId, res.dump());
         final Object buffer = makeFrame(header, res, ENVELOPE_RESPONSE);
         if (buffer != null) {
            writeFrame(buffer);
         }
      }
   }

   //   public Object makeFrame(Response res, int requestId, long contextId) {
   //      return makeFrame(, res, ENVELOPE_RESPONSE);
   //   }

   public void sendAltBroadcastMessage(Message msg, int toAltId) {
      final int myEntityId = getMyEntityId();
      if (myEntityId != 0) {
         final MessageHeader header = new MessageHeader(myEntityId, 0, theirId, toAltId, msg.getContractId(), msg.getStructId(),
               MessageHeader.FLAGS_ALTERNATE);
         CommsLogger.append(this, true, header, msg);
         //         if (!CommsLogger.commsLogIgnore(msg))
         //            commsLog("%s  [A] => %s (to altId-%d)", this, msg.dump(), toAltId);
         final Object buffer = makeFrame(header, msg, ENVELOPE_BROADCAST);
         if (buffer != null) {
            writeFrame(buffer);
            getDispatcher().messagesSentCounter.mark();
         }
      }
   }

   public void sendTopicBroadcastMessage(Message msg, int toId, int topicId) {
      final int myEntityId = getMyEntityId();
      if (myEntityId != 0) {
         final MessageHeader header = new MessageHeader(myEntityId, topicId, toId, 0, msg.getContractId(), msg.getStructId(), (byte) 0);
         CommsLogger.append(this, true, header, msg);
         //         if (!CommsLogger.commsLogIgnore(msg))
         //            commsLog("%s  [B] => %s (to TOPIC:%d-#%d)", this, msg.dump(), toId, topicId);
         final Object buffer = makeFrame(header, msg, ENVELOPE_BROADCAST);
         if (buffer != null) {
            writeFrame(buffer);
            getDispatcher().messagesSentCounter.mark();
         }
      }
   }

   public void sendMessage(Message msg, int toEntityId, int toChildId) {
      final int myEntityId = getMyEntityId();
      if (myEntityId != 0) {
         final MessageHeader header = new MessageHeader(myEntityId, 0, toEntityId, toChildId, msg.getContractId(), msg.getStructId(),
               (byte) 0);
         CommsLogger.append(this, true, header, msg);
         //         if (!CommsLogger.commsLogIgnore(msg))
         //            commsLog("%s  [M] => %s (to %d.%d)", this, msg.dump(), toEntityId, toChildId);
         final Object buffer = makeFrame(header, msg, ENVELOPE_MESSAGE);
         if (buffer != null) {
            writeFrame(buffer);
            getDispatcher().messagesSentCounter.mark();
         }
      }
   }

   private volatile boolean autoFlush = true;

   public void setAutoFlush(boolean val) {
      autoFlush = val;
   }

   public void flush() {
      channel.flush();
   }

   public ChannelFuture writeFrame(Object frame) {
      if (frame != null) {
         if (channel.isActive()) {
            lastSentTo.set(System.currentTimeMillis());
            if (autoFlush) {
               return channel.writeAndFlush(frame);
            } else {
               return channel.write(frame);
            }
         } else {
            ReferenceCountUtil.release(frame);
         }
      }
      return null;
   }

   public void sendRelayedMessage(MessageHeader header, ByteBuf payload, boolean broadcast) {
      assert header.fromId != 0;
      CommsLogger.append(this, true, header, payload);
      byte envelope = broadcast ? ENVELOPE_BROADCAST : ENVELOPE_MESSAGE;
      writeFrame(makeFrame(header, payload, envelope));
   }

   public Async sendRelayedRequest(RequestHeader header, ByteBuf payload, Session originator, ResponseHandler handler) {
      final Async async = new Async(null, header, originator, handler);
      //      int origRequestId = async.header.requestId;
      int newRequestId = addPendingRequest(async);
      CommsLogger.append(this, true, header, payload);
      //      if (!CommsLogger.commsLogIgnore(header.structId))
      //         commsLog("%s %016X [%d/%d] ~> Request:%s", this, header.contextId, newRequestId, origRequestId,
      //               StructureFactory.getName(header.contractId, header.structId));
      // making a new header lets us not worry about synchronizing the change the requestId
      RequestHeader newHeader = new RequestHeader(newRequestId, header.fromParentId, header.fromChildId, header.toId, header.fromType,
            header.timeout, header.version, header.contractId, header.structId, header.contextId);
      if (newHeader.toId == UNADDRESSED && (theirType != TYPE_TETRAPOD || header.contractId == TetrapodContract.CONTRACT_ID)) {
         newHeader.toId = theirId;
      }
      writeFrame(makeFrame(newHeader, payload, ENVELOPE_REQUEST));
      return async;
   }

   public void sendRelayedResponse(ResponseHeader header, Async async, ByteBuf payload) {
      CommsLogger.append(this, true, header, payload, async.header.structId);
      //      if (!CommsLogger.commsLogIgnore(header.structId))
      //         commsLog("%s %016X [%d] ~> Response:%s", this, header.contextId, header.requestId,
      //               StructureFactory.getName(header.contractId, header.structId));
      writeFrame(makeFrame(header, payload, ENVELOPE_RESPONSE));
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      ctx.close();
      // quieter logging for certain exceptions      
      if (cause instanceof IOException) {
         if (cause instanceof SSLException) {
            logger.warn("{} {}", this, cause.getMessage());
            return;
         } else if (cause.getMessage() != null) {
            if (cause.getMessage().equals("Connection reset by peer") || cause.getMessage().equals("Connection timed out")) {
               logger.info("{} {}", this, cause.getMessage());
               return;
            }
         }
      } else if (cause instanceof DecoderException) {
         logger.info("{} {}", this, cause.getMessage());
         return;
      }

      logger.error("{} : {} : {}", this, cause.getClass().getSimpleName(), cause.getMessage());
      logger.error(cause.getMessage(), cause);
   }

   public synchronized boolean isConnected() {
      if (channel != null) {
         return channel.isActive();
      }
      return false;
   }

   public void close() {
      channel.close();
   }

   public void addSessionListener(Listener listener) {
      synchronized (listeners) {
         listeners.add(listener);
      }
   }

   public void removeSessionListener(Listener listener) {
      synchronized (listeners) {
         listeners.remove(listener);
      }
   }

   protected void fireSessionStartEvent() {
      logger.debug("{} Session Start", this);
      for (Listener l : getListeners()) {
         l.onSessionStart(this);
      }
   }

   protected void fireSessionStopEvent() {
      logger.debug("{} Session Stop", this);
      for (Listener l : getListeners()) {
         l.onSessionStop(this);
      }
   }

   private Listener[] getListeners() {
      synchronized (listeners) {
         return listeners.toArray(new Listener[0]);
      }
   }

   public synchronized void setMyEntityId(int entityId) {
      if (myId != entityId && isConnected()) {
         logger.debug("{} Setting my Entity {}", this, entityId);
      }
      this.myId = entityId;
   }

   public synchronized int getMyEntityId() {
      return myId;
   }

   public void setMyEntityType(byte type) {
      myType = type;
   }

   public synchronized void setTheirEntityId(int entityId) {
      this.theirId = entityId;
   }

   public synchronized int getTheirEntityId() {
      return theirId;
   }

   public synchronized byte getTheirEntityType() {
      return theirType;
   }

   public void setTheirEntityType(byte type) {
      theirType = type;
   }

   public int addPendingRequest(Async async) {
      int requestId = requestCounter.incrementAndGet();
      pendingRequests.put(requestId, async);
      return requestId;
   }

   public void cancelAllPendingRequests() {
      for (Async a : pendingRequests.values()) {
         a.setResponse(ERROR_CONNECTION_CLOSED);
      }
      pendingRequests.clear();
   }

   // /////////////////////////////////// RELAY /////////////////////////////////////

   public synchronized void setRelayHandler(RelayHandler relayHandler) {
      this.relayHandler = relayHandler;
   }

   public String getPeerHostname() {
      if (channel != null && channel.remoteAddress() != null && channel.remoteAddress().getAddress() != null) {
         channel.remoteAddress().getAddress().getHostAddress();
      }
      return "Unknown";
   }

   //   @Deprecated
   //   public boolean commsLog(String format, Object... args) {
   //      return CommsLogger.commsLog(this, format, args);
   //   }

   public String getNameFor(MessageHeader header) {
      return StructureFactory.getName(header.contractId, header.structId);
   }

}
