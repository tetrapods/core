package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.ReferenceCountUtil;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;
import io.tetrapod.protocol.core.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.slf4j.*;

/**
 * Manages a session between two tetrapods
 */
public class Session extends ChannelInboundHandlerAdapter {

   public static interface Helper {
      public Structure make(int contractId, int structId);

      public Dispatcher getDispatcher();

      public ServiceAPI getHandler(int contractId);

      public int getContractId();
   }

   public static interface RelayHandler {

      public Session getRelaySession(int entityId);

      public void broadcast(MessageHeader header, ByteBuf buf);

   }

   public static final Logger         logger          = LoggerFactory.getLogger(Session.class);

   private static final int           WIRE_VERSION    = 1;
   private static final int           WIRE_OPTIONS    = 0x00000000;

   private static final AtomicInteger sessionCounter  = new AtomicInteger();

   private final SocketChannel        channel;
   private final int                  sessionNum      = sessionCounter.incrementAndGet();

   private final List<Listener>       listeners       = new LinkedList<Listener>();
   private final Map<Integer, Async>  pendingRequests = new ConcurrentHashMap<>();
   private final AtomicInteger        requestCounter  = new AtomicInteger();
   private final Session.Helper       helper;
   private RelayHandler               relayHandler;
   private final AtomicLong           lastHeardFrom   = new AtomicLong();
   private final AtomicLong           lastSentTo      = new AtomicLong();

   private boolean                    needsHandshake  = true;

   private int                        myId            = 0;
   private byte                       myType          = Core.TYPE_ANONYMOUS;

   private int                        theirId         = 0;
   private byte                       theirType       = Core.TYPE_ANONYMOUS;

   private int                        myContractId;

   public Session(SocketChannel channel, Session.Helper helper) {
      this.channel = channel;
      this.helper = helper;
      this.myContractId = helper.getContractId();
      channel.pipeline().addLast(new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 0));
      channel.pipeline().addLast(this);
   }

   /**
    * Listeners of session life-cycle events
    */
   public interface Listener {
      public void onSessionStart(Session ses);

      public void onSessionStop(Session ses);
   }

   public Dispatcher getDispatcher() {
      return helper.getDispatcher();
   }

   public int getSessionNum() {
      return sessionNum;
   }

   public SocketChannel getChannel() {
      return channel;
   }

   @Override
   public String toString() {
      return String.format("Ses%d[0x%08X]", sessionNum, myId);
   }

   private synchronized boolean needsHandshake() {
      return needsHandshake;
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      lastHeardFrom.set(System.currentTimeMillis());
      try {
         final ByteBuf in = (ByteBuf) msg;
         final int len = in.readInt() - 1;
         final byte envelope = in.readByte();
         logger.trace("{} channelRead {}", this, envelope);
         if (needsHandshake()) {
            readHandshake(in, envelope);
            fireSessionStartEvent();
         } else {
            read(in, len, envelope);
         }
      } finally {
         ReferenceCountUtil.release(msg);
      }
   }

   private void read(final ByteBuf in, final int len, final byte envelope) throws IOException {
      assert (len == in.readableBytes());
      logger.trace("Read message {} with {} bytes", envelope, len);
      switch (envelope) {
         case ENVELOPE_REQUEST:
            readRequest(in);
            break;
         case ENVELOPE_RESPONSE:
            readResponse(in);
            break;
         case ENVELOPE_MESSAGE:
            readMessage(in);
            break;
         case ENVELOPE_PING:
            sendPong();
            break;
         case ENVELOPE_PONG:
            break;
         default:
            logger.error("{} Unexpected Envelope Type {}", this, envelope);
            close();
      }
   }

   private void readHandshake(final ByteBuf in, final byte envelope) throws IOException {
      if (envelope != ENVELOPE_HANDSHAKE) {
         throw new IOException(this + "handshake not valid");
      }
      int theirVersion = in.readInt();
      @SuppressWarnings("unused")
      int theirOptions = in.readInt();
      if (theirVersion != WIRE_VERSION) {
         throw new IOException(this + "handshake version does not match " + theirVersion);
      }
      logger.trace("{} handshake succeeded", this);
      synchronized (this) {
         needsHandshake = false;
      }
   }

   private void readResponse(final ByteBuf in) throws IOException {
      final ByteBufDataSource reader = new ByteBufDataSource(in);
      final ResponseHeader header = new ResponseHeader();
      header.read(reader);

      logger.info("{}, READ RESPONSE: [{}]", this, header.requestId);
      final Async async = pendingRequests.remove(header.requestId);
      if (async != null) {
         if (async.header.fromId == myId) {
            final Response res = (Response) helper.make(async.header.contractId, header.structId);
            if (res != null) {
               res.read(reader);
               logger.debug("{}, Got Response: {}", this, res);
               getDispatcher().dispatch(new Runnable() {
                  public void run() {
                     async.setResponse(res);
                  }
               });
            } else {
               logger.warn("{} Could not find response structure {}", this, header.structId);
            }
         } else if (relayHandler != null) {
            relayResponse(header, async, in);
         }
      } else {
         logger.warn("{} Could not find pending request for {}", this, header.dump());
      }
   }

   private void readRequest(final ByteBuf in) throws IOException {
      final ByteBufDataSource reader = new ByteBufDataSource(in);
      final RequestHeader header = new RequestHeader();
      header.read(reader);

      // set/clobber with known details, unless it's from a trusted tetrapod
      if (theirType != TYPE_TETRAPOD) {
         header.fromId = theirId;
         header.fromType = theirType;
      }

      logger.debug("{}, READ REQUEST: [{}]", this, header.requestId);
      if ((header.toId == UNADDRESSED && header.contractId == myContractId) || header.toId == myId) {
         final Request req = (Request) helper.make(header.contractId, header.structId);
         if (req != null) {
            req.read(reader);
            dispatchRequest(header, req);
         } else {
            logger.warn("Could not find request structure {}", header.structId);
            sendResponse(new Error(ERROR_SERIALIZATION), header.requestId);
         }
      } else if (relayHandler != null) {
         relayRequest(header, in);
      }
   }

   private void readMessage(ByteBuf in) throws IOException {
      final ByteBufDataSource reader = new ByteBufDataSource(in);
      final MessageHeader header = new MessageHeader();
      header.read(reader);
      if (theirType != TYPE_TETRAPOD) {
         // fromId MUST be their id, unless it's a tetrapod session, which could be relaying
         header.fromId = theirId;
      }
      logger.debug("{}, READ MESSAGE: [{}]", this, header.dump());
      if ((header.toId == UNADDRESSED && header.contractId == myContractId) || header.toId == myId) {
         final Message msg = (Message) helper.make(header.contractId, header.structId);
         if (msg != null) {
            msg.read(reader);
            dispatchMessage(header, msg);
         } else {
            logger.warn("Could not find message structure {}", header.structId);
         }
      } else if (relayHandler != null) {
         relayMessage(header, in);
      }
   }

   private void dispatchRequest(final RequestHeader header, final Request req) {
      logger.debug("Got Request: {}", req);
      final ServiceAPI svc = helper.getHandler(header.contractId);
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

   private void dispatchMessage(final MessageHeader header, final Message msg) {
      // OPTIMIZE: use senderId to queue instead of using this single threaded queue 
      getDispatcher().dispatchSequential(new Runnable() {
         public void run() {
            logger.info("{} I GOT A MESSAGE: {}", this, msg.dump());
            // TODO: fire to listeners
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

      final ByteBuf buffer = makeFrame(header, req, ENVELOPE_REQUEST);
      if (buffer != null) {
         write(buffer);
      } else {
         async.setResponse(ERROR_SERIALIZATION);
      }
      return async;
   }

   private void sendResponse(Response res, int requestId) {
      logger.debug("{} sending response [{}]", this, requestId);
      final ByteBuf buffer = makeFrame(new ResponseHeader(requestId, res.getStructId()), res, ENVELOPE_RESPONSE);
      if (buffer != null) {
         write(buffer);
      }
   }

   public void sendMessage(Message msg, int toEntityId, int topicId) {
      logger.debug("{} sending message [{}]", this, msg.getStructId());
      final ByteBuf buffer = makeFrame(new MessageHeader(getMyEntityId(), topicId, toEntityId, msg.getContractId(), msg.getStructId()),
            msg, ENVELOPE_MESSAGE);
      if (buffer != null) {
         write(buffer);
      }
   }

   private ByteBuf makeFrame(Structure header, Structure payload, byte envelope) {
      return makeFrame(header, payload, envelope, channel.alloc().buffer(128));
   }

   public static ByteBuf makeFrame(Structure header, Structure payload, byte envelope, ByteBuf buffer) {
      final ByteBufDataSource data = new ByteBufDataSource(buffer);
      buffer.writeInt(0);
      buffer.writeByte(envelope);
      try {
         header.write(data);
         payload.write(data);
         // go back and write message length, now that we know it
         buffer.setInt(0, buffer.writerIndex() - 4);
         return buffer;
      } catch (IOException e) {
         ReferenceCountUtil.release(buffer);
         logger.error(e.getMessage(), e);
         return null;
      }
   }

   protected ChannelFuture write(ByteBuf buffer) {
      lastSentTo.set(System.currentTimeMillis());
      return channel.writeAndFlush(buffer);
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      logger.error(cause.getMessage(), cause);
      ctx.close();
   }

   @Override
   public void channelActive(final ChannelHandlerContext ctx) throws Exception {
      writeHandshake();
      scheduleHealthCheck();
   }

   @Override
   public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      fireSessionStopEvent();
      // TODO: cancel all pending requests
   }

   private void writeHandshake() {
      logger.trace("{} Writing handshake", this);
      synchronized (this) {
         needsHandshake = true;
      }
      final ByteBuf buffer = channel.alloc().buffer(9);
      buffer.writeInt(9);
      buffer.writeByte(ENVELOPE_HANDSHAKE);
      buffer.writeInt(WIRE_VERSION);
      buffer.writeInt(WIRE_OPTIONS);
      channel.writeAndFlush(buffer);
      lastSentTo.set(System.currentTimeMillis());
   }

   private void sendPing() {
      final ByteBuf buffer = channel.alloc().buffer(5);
      buffer.writeInt(1);
      buffer.writeByte(ENVELOPE_PING);
      channel.writeAndFlush(buffer);
      lastSentTo.set(System.currentTimeMillis());
   }

   private void sendPong() {
      final ByteBuf buffer = channel.alloc().buffer(5);
      buffer.writeInt(1);
      buffer.writeByte(ENVELOPE_PONG);
      channel.writeAndFlush(buffer);
      lastSentTo.set(System.currentTimeMillis());
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

   private void fireSessionStartEvent() {
      logger.debug("{} Session Start", this);
      for (Listener l : getListeners()) {
         l.onSessionStart(this);
      }
   }

   private void fireSessionStopEvent() {
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

   // TODO: needs configurable timeouts
   public void checkHealth() {
      if (isConnected()) {
         final long now = System.currentTimeMillis();
         if (now - lastHeardFrom.get() > 5000 || now - lastSentTo.get() > 5000) {
            sendPing();
         } else if (now - lastHeardFrom.get() > 10000) {
            logger.warn("{} Timeout", this);
            close();
         }
      }
      // TODO: Timeout pending requests past their due
   }

   private void scheduleHealthCheck() {
      if (isConnected()) {
         getDispatcher().dispatch(1, TimeUnit.SECONDS, new Runnable() {
            public void run() {
               checkHealth();
            }
         });
      }
   }

   public synchronized boolean isConnected() {
      if (channel != null) {
         return channel.isActive();
      }
      return false;
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

   private int addPendingRequest(Async async) {
      async.header.requestId = requestCounter.incrementAndGet();
      pendingRequests.put(async.header.requestId, async);
      return async.header.requestId;
   }

   ///////////////////////////////////// RELAY /////////////////////////////////////

   public synchronized void setRelayHandler(RelayHandler relayHandler) {
      this.relayHandler = relayHandler;
   }

   private void relayRequest(final RequestHeader header, final ByteBuf in) {
      final Session ses = relayHandler.getRelaySession(header.toId);
      if (ses != null) {
         // OPTIMIZE: Find a way to relay without the byte[] allocation & copy
         final ByteBuf buffer = channel.alloc().buffer(32 + in.readableBytes());
         final ByteBufDataSource data = new ByteBufDataSource(buffer);

         final Async async = new Async(null, header, this);
         int origRequestId = async.header.requestId;
         try {
            ses.addPendingRequest(async);
            logger.debug("{} RELAYING REQUEST: [{}] was " + origRequestId, this, async.header.requestId);
            buffer.writeInt(0); // length placeholder
            buffer.writeByte(ENVELOPE_REQUEST);
            async.header.write(data);
            buffer.writeBytes(in);
            buffer.setInt(0, buffer.writerIndex() - 4); // go back and write message length, now that we know it
            ses.write(buffer);
         } catch (IOException e) {
            ReferenceCountUtil.release(buffer);
            logger.error(e.getMessage(), e);
         } finally {
            header.requestId = origRequestId;
         }
      } else {
         logger.warn("Could not find a relay session for {}", header.toId);
      }
   }

   private void relayResponse(ResponseHeader header, Async async, ByteBuf in) {
      logger.debug("{} RELAYING RESPONSE: [{}]", this, header.requestId);
      // OPTIMIZE: Find a way to relay without the extra allocation & copy
      final ByteBuf buffer = channel.alloc().buffer(32 + in.readableBytes());
      final ByteBufDataSource data = new ByteBufDataSource(buffer);
      try {
         buffer.writeInt(0);
         buffer.writeByte(ENVELOPE_RESPONSE);
         header.requestId = async.header.requestId;
         header.write(data);
         buffer.writeBytes(in);
         buffer.setInt(0, buffer.writerIndex() - 4); // Write message length, now that we know it
         async.session.write(buffer);
      } catch (IOException e) {
         ReferenceCountUtil.release(buffer);
         logger.error(e.getMessage(), e);
      }
   }

   private void relayMessage(final MessageHeader header, final ByteBuf payload) {
      logger.debug("{}, RELAY MESSAGE: [{}]", this, header.structId);
      if (header.toId == UNADDRESSED) {
         // TODO: Broadcast to all sessions we need to
         relayHandler.broadcast(header, payload);
      } else {
         final Session ses = relayHandler.getRelaySession(header.toId);
         if (ses != null) {
            ses.forwardMessage(header, payload);
         }
      }
   }

   protected void forwardMessage(MessageHeader header, ByteBuf payload) {
      payload.resetReaderIndex();
      payload.retain();
      write(payload);
   }

   public static String dumpBuffer(ByteBuf buf) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < buf.writerIndex(); i++) {
         sb.append(buf.getByte(i));
         sb.append(' ');
      }
      return sb.toString();
   }

}
