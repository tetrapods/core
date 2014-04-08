package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.*;
import static io.tetrapod.protocol.core.TetrapodContract.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.web.WebRoutes;
import io.tetrapod.protocol.core.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.slf4j.*;

/**
 * Manages a session between two tetrapods
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

      public ServiceAPI getServiceHandler(int contractId);

      public List<SubscriptionAPI> getMessageHandlers(int contractId, int structId);

      public int getContractId();
   }

   public static interface RelayHandler {

      public Session getRelaySession(int toId, int contractid);

      public void relayMessage(MessageHeader header, ByteBuf buf, boolean isBroadcast) throws IOException;

      public WebRoutes getWebRoutes();

   }

   protected static final Logger        logger                  = LoggerFactory.getLogger(Session.class);
   protected static final Logger        commsLog                = LoggerFactory.getLogger("comms");

   public static final byte             DEFAULT_REQUEST_TIMEOUT = 30;

   protected static final AtomicInteger sessionCounter          = new AtomicInteger();

   protected final int                  sessionNum              = sessionCounter.incrementAndGet();

   protected final List<Listener>       listeners               = new LinkedList<Listener>();
   protected final Map<Integer, Async>  pendingRequests         = new ConcurrentHashMap<>();
   protected final AtomicInteger        requestCounter          = new AtomicInteger();
   protected final Session.Helper       helper;
   protected final SocketChannel        channel;
   protected final AtomicLong           lastHeardFrom           = new AtomicLong();
   protected final AtomicLong           lastSentTo              = new AtomicLong();

   protected RelayHandler               relayHandler;

   protected int                        myId                    = 0;
   protected byte                       myType                  = Core.TYPE_ANONYMOUS;

   protected int                        theirId                 = 0;
   protected byte                       theirType               = Core.TYPE_ANONYMOUS;

   protected int                        myContractId;

   public Session(SocketChannel channel, Session.Helper helper) {
      this.channel = channel;
      this.helper = helper;
      this.myContractId = helper.getContractId();
   }

   /**
    * Check to see if this session is still alive and close it, if not
    */
   // TODO: needs configurable timeouts
   public void checkHealth() {
      if (isConnected()) {
         final long now = System.currentTimeMillis();
         if (now - lastHeardFrom.get() > 5000 || now - lastSentTo.get() > 5000) {
            sendPing();
         }
         if (now - lastHeardFrom.get() > 10000) {
            logger.warn("{} Timeout", this);
            close();
         }
      }
      // TODO: Timeout pending requests past their due
      for (Async a : pendingRequests.values()) {
         if (a.isTimedout()) {
            pendingRequests.remove(a.header.requestId);
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
      if (isConnected()) {
         getDispatcher().dispatch(1, TimeUnit.SECONDS, new Runnable() {
            public void run() {
               checkHealth();
               scheduleHealthCheck();
            }
         });
      }
   }

   @Override
   public String toString() {
      return String.format("%s #%d [0x%08X]", getClass().getSimpleName(), sessionNum, theirId);
   }

   protected String getStructName(int contractId, int structId) {
      Structure s = StructureFactory.make(contractId, structId);
      if (s == null) {
         return "Struct-" + structId;
      }
      return s.getClass().getSimpleName();
   }

   protected void dispatchRequest(final RequestHeader header, final Request req) {
      final ServiceAPI svc = helper.getServiceHandler(header.contractId);
      if (svc != null) {
         getDispatcher().dispatch(new Runnable() {
            public void run() {
               try {
                  dispatchRequest(svc, header, req);
               } catch (Throwable e) {
                  logger.error(e.getMessage(), e);
                  sendResponse(new Error(ERROR_UNKNOWN), header.requestId);
               }
               getDispatcher().requestsHandledCounter.increment();
            }
         });
      } else {
         logger.warn("{} No handler found for {}", this, header.dump());
         sendResponse(new Error(ERROR_UNKNOWN_REQUEST), header.requestId);
      }
   }

   private void dispatchRequest(final ServiceAPI svc, final RequestHeader header, final Request req) {
      RequestContext ctx = new RequestContext(header, this);
      Response res = req.securityCheck(ctx);
      if (res == null) {
         res = req.dispatch(svc, ctx);
      }
      if (res != null) {
         sendResponse(res, header.requestId);
      } else {
         sendResponse(new Error(ERROR_UNKNOWN), header.requestId);
      }
   }

   protected void dispatchMessage(final MessageHeader header, final Message msg) {
      // OPTIMIZE: use senderId to queue instead of using this single threaded queue
      final MessageContext ctx = new MessageContext(header, this);
      getDispatcher().dispatchSequential(new Runnable() {
         public void run() {
            for (SubscriptionAPI handler : helper.getMessageHandlers(header.contractId, header.structId)) {
               msg.dispatch(handler, ctx);
            }
         }
      });
   }

   public Response sendPendingRequest(Request req, int toId, byte timeoutSeconds, final PendingResponseHandler pendingHandler) {
      final Async async = sendRequest(req, toId, timeoutSeconds);
      async.handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            Response pendingRes = null;
            try {
               pendingRes = pendingHandler.onResponse(res);
            } catch (Throwable e) {
               logger.error(e.getMessage(), e);
            } finally {
               // finally return the pending response we were waiting on
               if (pendingRes == null) {
                  pendingRes = new Error(ERROR_UNKNOWN);
               }
               sendResponse(pendingRes, pendingHandler.originalRequestId);
            }
         }
      });
      return Response.PENDING;
   }

   public Async sendRequest(Request req, int toId) {
      return sendRequest(req, toId, DEFAULT_REQUEST_TIMEOUT);
   }

   public Async sendRequest(Request req, int toId, byte timeoutSeconds) {
      final RequestHeader header = new RequestHeader();
      header.requestId = requestCounter.incrementAndGet();
      header.toId = toId;
      header.fromId = myId;
      header.timeout = timeoutSeconds;
      header.contractId = req.getContractId();
      header.structId = req.getStructId();
      header.fromType = myType;

      final Async async = new Async(req, header, this);
      pendingRequests.put(header.requestId, async);

      commsLog("%s  [%d] => %s (to %d)", this, header.requestId, req.dump(), toId);

      final Object buffer = makeFrame(header, req, ENVELOPE_REQUEST);
      if (buffer != null) {
         if (channel.isActive()) {
            writeFrame(buffer);
         } else {
            async.setResponse(ERROR_CONNECTION_CLOSED);
         }
      } else {
         async.setResponse(ERROR_SERIALIZATION);
      }
      return async;
   }

   public void sendResponse(Response res, int requestId) {
      if (res != Response.PENDING) {
         commsLog("%s  [%d] => %s", this, requestId, res.dump());
         final Object buffer = makeFrame(new ResponseHeader(requestId, res.getContractId(), res.getStructId()), res, ENVELOPE_RESPONSE);
         if (buffer != null) {
            writeFrame(buffer);
         }
      }
   }

   public void sendBroadcastMessage(Message msg, int topicId) {
      if (getMyEntityId() != 0) {
         commsLog("%s  [B] => T%d.%s", this, topicId, msg.dump());
         final Object buffer = makeFrame(
               new MessageHeader(getMyEntityId(), topicId, Core.UNADDRESSED, msg.getContractId(), msg.getStructId()), msg,
               ENVELOPE_BROADCAST);
         if (buffer != null) {
            writeFrame(buffer);
            getDispatcher().messagesSentCounter.increment();
         }
      }
   }

   public void sendMessage(Message msg, int toEntityId, int topicId) {
      if (getMyEntityId() != 0) {
         commsLog("%s  [M] => T%d.%s (to %d)", this, topicId, msg.dump(), toEntityId);
         final Object buffer = makeFrame(new MessageHeader(getMyEntityId(), topicId, toEntityId, msg.getContractId(), msg.getStructId()),
               msg, ENVELOPE_MESSAGE);
         if (buffer != null) {
            writeFrame(buffer);
            getDispatcher().messagesSentCounter.increment();
         }
      }
   }

   public ChannelFuture writeFrame(Object frame) {
      if (frame != null && channel.isActive()) {
         lastSentTo.set(System.currentTimeMillis());
         return channel.writeAndFlush(frame);
      }
      return null;
   }

   public void sendRelayedMessage(MessageHeader header, ByteBuf payload, boolean broadcast) {
      commsLog("%s  [M] ~> T%d.Message:%d %s (to %d)", this, header.topicId, header.structId, getNameFor(header), header.toId);
      byte envelope = broadcast ? ENVELOPE_BROADCAST : ENVELOPE_MESSAGE;
      writeFrame(makeFrame(header, payload, envelope));
   }

   public void sendRelayedRequest(RequestHeader header, ByteBuf payload, Session originator) {
      final Async async = new Async(null, header, originator);
      int origRequestId = async.header.requestId;
      int newRequestId = addPendingRequest(async);
      commsLog("%s  [%d/%d] ~> Request:%d", this, newRequestId, origRequestId, header.structId);
      // making a new header lets us not worry about synchronizing the change the requestId
      RequestHeader newHeader = new RequestHeader(newRequestId, header.fromId, header.toId, header.fromType, header.timeout,
            header.version, header.contractId, header.structId);
      writeFrame(makeFrame(newHeader, payload, ENVELOPE_REQUEST));
   }

   public void sendRelayedResponse(ResponseHeader header, ByteBuf payload) {
      commsLog("%s  [%d] ~> Response:%d", this, header.requestId, header.structId);
      writeFrame(makeFrame(header, payload, ENVELOPE_RESPONSE));
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      logger.error(cause.getMessage(), cause);
      ctx.close();
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
      return "Unknown";
   }

   public boolean commsLog(String format, Object... args) {
      //logger.debug(String.format(format, args));
      if (commsLog.isInfoEnabled()) {
         for (int i = 0; i < args.length; i++) {
            if (args[i] == this) {
               int h = Thread.currentThread().getName().hashCode() & 0xFFFFFF;
               args[i] = String.format("{%06x} %s:%d", h, getClass().getSimpleName().substring(0, 4), sessionNum);
            }
         }
         commsLog.info(String.format(format, args));
      } else {
         if (logger.isTraceEnabled()) {
            logger.trace(String.format(format, args));
         }
      }
      return true;
   }

   public String getNameFor(MessageHeader header) {
      return StructureFactory.getName(header.contractId, header.structId);
   }

}
