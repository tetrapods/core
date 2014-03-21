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

      public void execute(Runnable runnable);

      public ScheduledFuture<?> execute(int delay, TimeUnit unit, Runnable runnable);

      public ServiceAPI getHandler(int contractId);

      public Session getRelaySession(int entityId);

      public int getContractId();
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
   private final AtomicLong           lastHeardFrom   = new AtomicLong();
   private final AtomicLong           lastSentTo      = new AtomicLong();

   private boolean                    needsHandshake  = true;

   private int                        myId            = 0;
   private byte                       myType          = Core.TYPE_SERVICE;
   private int                        myContractId;
   private boolean                    untrusted       = true;

   public Session(SocketChannel channel, Session.Helper helper) {
      this.channel = channel;
      this.helper = helper;
      this.myContractId = helper.getContractId();
      channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
         @Override
         public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.fireChannelRead(msg);
         }
      });
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
      logger.info("{} handshake succeeded", this);
      synchronized (this) {
         needsHandshake = false;
      }
   }

   private void readResponse(final ByteBuf in) throws IOException {
      final ByteBufDataSource reader = new ByteBufDataSource(in);
      final ResponseHeader header = new ResponseHeader();
      header.read(reader);

      logger.debug("{}, READ RESPONSE: [{}]", this, header.requestId);
      final Async async = pendingRequests.remove(header.requestId);
      if (async != null) {
         if (async.header.fromId == myId) {
            final Response res = (Response) helper.make(async.header.contractId, header.structId);
            if (res != null) {
               res.read(reader);
               logger.debug("{}, Got Response: {}", this, res);
               helper.execute(new Runnable() {
                  public void run() {
                     if (res.getStructId() == Error.STRUCT_ID) {
                        async.setResponse(null, ((Error) res).code);
                     } else {
                        async.setResponse(res, 0);
                     }
                  }
               });
            } else {
               logger.warn("{} Could not find response structure {}", this, header.structId);
            }
         } else {
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
      if (untrusted && header.fromType != TYPE_ANONYMOUS && header.fromType != TYPE_CLIENT) {
         header.fromType = TYPE_ANONYMOUS;
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
      } else {
         relayRequest(header, in);
      }
   }

   private void readMessage(ByteBuf in) throws IOException {
      final ByteBufDataSource reader = new ByteBufDataSource(in);
      final MessageHeader header = new MessageHeader();
      header.read(reader);
      logger.debug("{}, READ MESSAGE: [{}]", this, header.dump());
      if ((header.toId == UNADDRESSED && header.contractId == myContractId) || header.toId == myId) {
         final Message msg = (Message) helper.make(header.contractId, header.structId);
         if (msg != null) {
            msg.read(reader);
            dispatchMessage(header, msg);
         } else {
            logger.warn("Could not find message structure {}", header.structId);
         }
      } else {
         relayMessage(header, in);
      }
   }

   private void relayMessage(MessageHeader header, ByteBuf in) {
      final ByteBuf buffer = channel.alloc().buffer(in.writableBytes());
      try {
         buffer.writeBytes(in, 0, in.writerIndex());
      } catch (Exception e) {
         ReferenceCountUtil.release(buffer);
         logger.error(e.getMessage(), e);
         return;
      }

      logger.debug("{}, RELAY MESSAGE: [{}]", this, header.dump());
      if (header.toId == UNADDRESSED) {
         // TODO: Broadcast to all sessions we need to
      } else {
         final Session ses = helper.getRelaySession(header.toId);
         if (ses != null) {
            ses.write(buffer);
         }
      }
   }

   private void relayRequest(final RequestHeader header, final ByteBuf in) {
      final Session ses = helper.getRelaySession(header.toId);
      if (ses != null) {
         // OPTIMIZE: Find a way to relay without the byte[] allocation & copy
         final Async async = new Async(null, header, this);
         final byte[] payload = new byte[in.readableBytes()];
         in.getBytes(in.readerIndex(), payload);
         ses.sendRelayRequest(async, payload);
      } else {
         logger.warn("Could not find a relay session for {}", header.toId);
      }
   }

   private void relayResponse(ResponseHeader header, Async async, ByteBuf in) {
      // OPTIMIZE: Find a way to relay without the byte[] allocation & copy
      final byte[] payload = new byte[in.readableBytes()];
      in.getBytes(in.readerIndex(), payload);
      async.session.sendRelayResponse(new ResponseHeader(async.header.requestId, header.structId), payload);
   }

   private void dispatchRequest(final RequestHeader header, final Request req) {
      logger.debug("Got Request: {}", req);
      final ServiceAPI svc = helper.getHandler(header.contractId);
      if (svc != null) {
         helper.execute(new Runnable() {
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

   private void dispatchMessage(MessageHeader header, Message msg) {
      // FIXME: Execute dispatch on sequential queue for sender
      logger.info("{} I GOT A MESSAGE: {}", this, msg.dump());
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

      if (!writeFrame(header, req, ENVELOPE_REQUEST)) {
         async.setResponse(null, ERROR_SERIALIZATION);
      }
      return async;
   }

   private void sendResponse(Response res, int requestId) {
      logger.debug("{} sending response [{}]", this, requestId);
      writeFrame(new ResponseHeader(requestId, res.getStructId()), res, ENVELOPE_RESPONSE);
   }

   public void sendMessage(Message msg, int toEntityId, int topicId) {
      logger.debug("{} sending message [{}]", this, msg.getStructId());
      writeFrame(new MessageHeader(getEntityId(), topicId, toEntityId, msg.getStructId(), msg.getContractId()), msg, ENVELOPE_MESSAGE);
   }

   private boolean writeFrame(Structure header, Structure payload, byte envelope) {
      final ByteBuf buffer = channel.alloc().buffer(128);
      final ByteBufDataSource data = new ByteBufDataSource(buffer);
      buffer.writeInt(0);
      buffer.writeByte(envelope);
      try {
         header.write(data);
         payload.write(data);
         // go back and write message length, now that we know it
         buffer.setInt(0, buffer.writerIndex() - 4);
         write(buffer);
         return true;
      } catch (IOException e) {
         ReferenceCountUtil.release(buffer);
         logger.error(e.getMessage(), e);
      }
      return false;
   }

   private boolean sendRelayRequest(final Async async, byte[] payload) {
      // We need a different requestId for the relay request
      int origRequestId = async.header.requestId;
      async.header.requestId = requestCounter.incrementAndGet();
      pendingRequests.put(async.header.requestId, async);

      logger.debug("{} RELAYING REQUEST: [{}] ", this, async.header.requestId);

      final ByteBuf buffer = channel.alloc().buffer(32 + payload.length);
      final ByteBufDataSource data = new ByteBufDataSource(buffer);
      buffer.writeInt(0);
      buffer.writeByte(ENVELOPE_REQUEST);

      try {
         async.header.write(data);
         async.header.requestId = origRequestId;
         buffer.writeBytes(payload);
         // go back and write message length, now that we know it
         buffer.setInt(0, buffer.writerIndex() - 4);
         write(buffer);
         return true;
      } catch (IOException e) {
         ReferenceCountUtil.release(buffer);
         logger.error(e.getMessage(), e);
      }
      return false;
   }

   private boolean sendRelayResponse(ResponseHeader header, byte[] payload) {
      logger.debug("{} RELAYING RESPONSE: [{}]", this, header.requestId);

      final ByteBuf buffer = channel.alloc().buffer(32 + payload.length);
      final ByteBufDataSource data = new ByteBufDataSource(buffer);
      buffer.writeInt(0);
      buffer.writeByte(ENVELOPE_RESPONSE);

      try {
         header.write(data);
         buffer.writeBytes(payload);
         // go back and write message length, now that we know it
         buffer.setInt(0, buffer.writerIndex() - 4);
         write(buffer);
         return true;
      } catch (IOException e) {
         ReferenceCountUtil.release(buffer);
         logger.error(e.getMessage(), e);
      }
      return false;
   }

   private ChannelFuture write(ByteBuf in) {
      lastSentTo.set(System.currentTimeMillis());
      return channel.writeAndFlush(in);
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
      logger.debug("{} Writing handshake", this);
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
         helper.execute(1, TimeUnit.SECONDS, new Runnable() {
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

   public synchronized void setEntityId(int entityId) {
      this.myId = entityId;
   }

   public synchronized int getEntityId() {
      return myId;
   }

   public void setEntityType(byte type) {
      myType = type;
   }

   public void setUntrusted(boolean b) {
      untrusted = b;
   }

}
