package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.web.WebRoutes;
import io.tetrapod.protocol.core.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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

      public List<SubscriptionAPI> getMessageHandlers(int contractId, int structUd);

      public int getContractId();
   }

   public static interface RelayHandler {

      public Session getRelaySession(int toId, int contractid);

      public void broadcast(MessageHeader header, ByteBuf buf);

      public WebRoutes getWebRoutes();

   }

   private static final Logger          logger          = LoggerFactory.getLogger(Session.class);

   protected static final AtomicInteger sessionCounter  = new AtomicInteger();

   protected final int                  sessionNum      = sessionCounter.incrementAndGet();

   protected final List<Listener>       listeners       = new LinkedList<Listener>();
   protected final Map<Integer, Async>  pendingRequests = new ConcurrentHashMap<>();
   protected final AtomicInteger        requestCounter  = new AtomicInteger();
   protected final Session.Helper       helper;
   protected final SocketChannel        channel;

   protected RelayHandler               relayHandler;

   protected int                        myId            = 0;
   protected byte                       myType          = Core.TYPE_ANONYMOUS;

   protected int                        theirId         = 0;
   protected byte                       theirType       = Core.TYPE_ANONYMOUS;

   protected int                        myContractId;

   public Session(SocketChannel channel, Session.Helper helper) {
      this.channel = channel;
      this.helper = helper;
      this.myContractId = helper.getContractId();
   }

   abstract protected Object makeFrame(Structure header, Structure payload, byte envelope);

   abstract protected Object makeFrame(Structure header, ByteBuf payload, byte envelope);

   public Dispatcher getDispatcher() {
      return helper.getDispatcher();
   }

   public int getSessionNum() {
      return sessionNum;
   }

   @Override
   public String toString() {
      return String.format("Ses%d [0x%08X : 0x%08X]", sessionNum, myId, theirId);
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
            }
         });
      } else {
         logger.warn("{} No handler found for {}", this, header.dump());
         sendResponse(new Error(ERROR_UNKNOWN_REQUEST), header.requestId);
      }
   }

   private void dispatchRequest(final ServiceAPI svc, final RequestHeader header, final Request req) {
      final RequestContext ctx = new RequestContext(header, this);
      final Response res = req.dispatch(svc, ctx);
      // TODO: Pending responses
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

      logger.debug(String.format("%s > REQUEST [%d] %d %s", this, toId, header.requestId, req.getClass().getSimpleName()));

      final Object buffer = makeFrame(header, req, ENVELOPE_REQUEST);
      if (buffer != null) {
         writeFrame(buffer);
      } else {
         async.setResponse(ERROR_SERIALIZATION);
      }
      return async;
   }

   public void sendResponse(Response res, int requestId) {
      logger.debug(String.format("%s > RESPONSE [%d] %s", this, requestId, res.getClass().getSimpleName()));
      final Object buffer = makeFrame(new ResponseHeader(requestId, res.getContractId(), res.getStructId()), res, ENVELOPE_RESPONSE);
      if (buffer != null) {
         writeFrame(buffer);
      }
   }

   public void sendBroadcastMessage(Message msg, int topicId) {
      logger.debug(String.format("%s > MESSAGE [%d] %s", this, topicId, msg.getClass().getSimpleName()));
      final Object buffer = makeFrame(
            new MessageHeader(getMyEntityId(), topicId, Core.UNADDRESSED, msg.getContractId(), msg.getStructId()), msg, ENVELOPE_BROADCAST);
      if (buffer != null) {
         writeFrame(buffer);
      }
   }

   public void sendMessage(Message msg, int toEntityId, int topicId) {
      logger.debug(String.format("%s > MESSAGE [%d:%d] %s", this, toEntityId, topicId, msg.getClass().getSimpleName()));
      final Object buffer = makeFrame(new MessageHeader(getMyEntityId(), topicId, toEntityId, msg.getContractId(), msg.getStructId()), msg,
            ENVELOPE_MESSAGE);
      if (buffer != null) {
         writeFrame(buffer);
      }
   }

   public ChannelFuture writeFrame(Object frame) {
      if (frame != null)
         return channel.writeAndFlush(frame);
      return null;
   }

   public void sendRelayedMessage(MessageHeader header, ByteBuf payload, boolean broadcast) {
      logger.debug("{}, RELAYING MESSAGE: [{}]", this, header.structId);
      byte envelope = broadcast ? ENVELOPE_BROADCAST : ENVELOPE_MESSAGE;
      writeFrame(makeFrame(header, payload, envelope));
   }

   public void sendRelayedRequest(RequestHeader header, ByteBuf payload, Session originator) {
      final Async async = new Async(null, header, originator);
      int origRequestId = async.header.requestId;
      this.addPendingRequest(async);
      logger.debug("{} RELAYING REQUEST: [{}] was " + origRequestId, this, async.header.requestId);
      writeFrame(makeFrame(header, payload, ENVELOPE_REQUEST));
      header.requestId = origRequestId;
   }

   public void sendRelayedResponse(ResponseHeader header, ByteBuf payload) {
      logger.debug("{} RELAYING RESPONSE: [{}]", this, header.requestId);
      writeFrame(makeFrame(header, payload, ENVELOPE_RESPONSE));
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      logger.error(cause.getMessage(), cause);
      ctx.close();
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
      async.header.requestId = requestCounter.incrementAndGet();
      pendingRequests.put(async.header.requestId, async);
      return async.header.requestId;
   }

   // /////////////////////////////////// RELAY /////////////////////////////////////

   public synchronized void setRelayHandler(RelayHandler relayHandler) {
      this.relayHandler = relayHandler;
   }

   public String getPeerHostname() {
      return "Unknown";
   }

}
