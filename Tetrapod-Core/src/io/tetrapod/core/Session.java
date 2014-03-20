package io.tetrapod.core;

import static io.tetrapod.core.rpc.Request.*;
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

      public void relayRequest(final RequestHeader header, final ByteBuf in, final Session fromSession);
   }

   public static final Logger         logger             = LoggerFactory.getLogger(Session.class);

   private static final int           WIRE_VERSION       = 1;
   private static final int           WIRE_OPTIONS       = 0x00000000;

   private static final byte          ENVELOPE_HANDSHAKE = 1;
   private static final byte          ENVELOPE_REQUEST   = 2;
   private static final byte          ENVELOPE_RESPONSE  = 3;
   private static final byte          ENVELOPE_MESSAGE   = 4;
   private static final byte          ENVELOPE_PING      = 5;
   private static final byte          ENVELOPE_PONG      = 6;

   private static final AtomicInteger sessionCounter     = new AtomicInteger();

   private final SocketChannel        channel;
   private final int                  sessionNum         = sessionCounter.incrementAndGet();

   private final List<Listener>       listeners          = new LinkedList<Listener>();
   private final Map<Integer, Async>  pendingRequests    = new ConcurrentHashMap<>();
   private final AtomicInteger        requestCounter     = new AtomicInteger();
   private final Session.Helper       helper;
   private final AtomicLong           lastHeardFrom      = new AtomicLong();
   private final AtomicLong           lastSentTo         = new AtomicLong();

   private boolean                    needsHandshake     = true;

   private int                        myId               = 0;

   public Session(SocketChannel channel, Session.Helper helper) {
      this.channel = channel;
      this.helper = helper;
      channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
         @Override
         public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            final ByteBuf in = (ByteBuf) msg;
            logger.debug("DEBUG: channelRead {} ", in.readableBytes());
            ctx.fireChannelRead(msg);
         }
      });
      channel.pipeline().addLast(new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4));
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
      return String.format("Session-0x%08X", sessionNum);
   }

   private synchronized boolean needsHandshake() {
      return needsHandshake;
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      lastHeardFrom.set(System.currentTimeMillis());
      try {
         final ByteBuf in = (ByteBuf) msg;
         final byte envelope = in.readByte();
         logger.debug("channelRead {} {} ", envelope, in.readableBytes());
         if (needsHandshake()) {
            readHandshake(in, envelope);
            fireSessionStartEvent();
         } else {
            read(in, envelope);
         }
      } finally {
         ReferenceCountUtil.release(msg);
      }
   }

   private void read(final ByteBuf in, final byte envelope) throws IOException {
      logger.debug("Read message {} with {} bytes", envelope, in.readableBytes());
      switch (envelope) {
         case ENVELOPE_REQUEST:
            readRequest(in);
            break;
         case ENVELOPE_RESPONSE:
            readResponse(in);
            break;
         case ENVELOPE_MESSAGE:
            // TODO:
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
      final Response res = (Response) helper.make(header.contractId, header.structId);
      if (res != null) {
         res.read(reader);
         logger.debug("Got Response: {}", res);
         final Async async = pendingRequests.get(header.requestId);
         if (async != null) {
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
            logger.warn("Could not find pending request {} for {}", header.requestId, res);
         }
      } else {
         logger.warn("Could not find structure {}", header.structId);
      }
   }

   private void readRequest(final ByteBuf in) throws IOException {
      final ByteBufDataSource reader = new ByteBufDataSource(in);
      final RequestHeader header = new RequestHeader();
      header.read(reader);
      // requests addressed to 0 are intended for us
      if (header.toId == RequestHeader.TO_ID_DIRECT || header.toId == myId) {
         final Request req = (Request) helper.make(header.contractId, header.structId);
         if (req != null) {
            req.read(reader);
            dispatchRequest(header, req);
         } else {
            logger.warn("Could not find structure {}", header.structId);
            sendResponse(new Error(ERROR_SERIALIZATION), header.requestId);
         }
      } else {
         helper.relayRequest(header, in, this);
      }
   }

   private void dispatchRequest(final RequestHeader header, final Request req) {
      logger.debug("Got Request: {}", req);
      final ServiceAPI svc = findServiceHandler(header.structId);
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
         logger.warn("No handler found for {} {}", header.structId, header);
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

   private ServiceAPI findServiceHandler(int structId) {
      // FIXME -- registered handlers map needed
      if (structId == RegisterRequest.STRUCT_ID) {
         // return new TetrapodService();
      }
      return null;
   }

   public Async sendRequest(Request req, int toId, int fromId, byte fromType, byte timeoutSeconds) {
      final RequestHeader header = new RequestHeader();
      header.requestId = requestCounter.incrementAndGet();
      header.toId = toId;
      header.fromId = fromId;
      header.timeout = timeoutSeconds;
      header.structId = req.getStructId();
      header.fromType = fromType;

      final Async async = new Async(req, header.requestId);
      pendingRequests.put(header.requestId, async);

      if (!writeFrame(header, req, ENVELOPE_REQUEST)) {
         async.setResponse(null, Request.ERROR_SERIALIZATION);
      }
      return async;
   }

   public Async sendRequest(final RequestHeader header, final Request req) {
      final Async async = new Async(req, header.requestId);
      pendingRequests.put(header.requestId, async);

      if (!writeFrame(header, req, ENVELOPE_REQUEST)) {
         async.setResponse(null, Request.ERROR_SERIALIZATION);
      }
      return async;
   }

   protected void sendResponse(Response res, int requestId) {
      final ResponseHeader header = new ResponseHeader();
      header.requestId = requestId;
      header.structId = res.getStructId();
      if (!writeFrame(header, res, ENVELOPE_RESPONSE)) {
         // TODO
      }
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
         channel.writeAndFlush(buffer);
         lastSentTo.set(System.currentTimeMillis());
         return true;
      } catch (IOException e) {
         ReferenceCountUtil.release(buffer);
         logger.error(e.getMessage(), e);
      }
      return false;
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
   }

   private void writeHandshake() {
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

}
