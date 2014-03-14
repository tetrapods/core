package io.tetrapod.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.ReferenceCountUtil;
import io.tetrapod.core.protocol.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.datasources.ByteBufDatasource;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

import org.slf4j.*;

/**
 * Manages a session between two tetrapods
 */
public class Session extends ChannelInboundHandlerAdapter {

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
   private final Dispatcher           dispatcher;

   private final List<Listener>       listeners          = new LinkedList<Listener>();

   private boolean                    needsHandshake     = true;
   private AtomicLong                 lastHeardFrom      = new AtomicLong();

   public Session(SocketChannel channel, Dispatcher dispatcher) {
      this.channel = channel;
      this.dispatcher = dispatcher;
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

   public Dispatcher getDispatcher() {
      return dispatcher;
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

   private void readResponse(ByteBuf in) {
      // TODO
   }

   private void readRequest(ByteBuf in) throws IOException {
      log(in);
      final ByteBufDatasource reader = new ByteBufDatasource(in);
      final RequestHeader header = new RequestHeader();
      header.read(reader); // FIXME: Not working :-( Reading 1 byte and returning empty struct
      final Request req = (Request) makeStruct(header.structId);
      if (req != null) {
         req.read(reader);
         // FIXME: DISPATCH
         logger.debug("Got Request {}", req);
      } else {
         logger.warn("Could not find structure {}", header.structId);
      }
   }

   public Async sendRequest(Request req, int toId, int fromId, byte timeoutSeconds) {
      final Async async = new Async(req);
      final RequestHeader header = new RequestHeader();
      header.requestId = -1; // FIXME - need pending map
      header.toId = toId;
      header.fromId = fromId;
      header.timeout = timeoutSeconds;
      header.structId = req.getStructId();
      //header.fromType // FIXME

      final ByteBuf buffer = channel.alloc().buffer(128);
      final ByteBufDatasource data = new ByteBufDatasource(buffer);
      buffer.writeInt(0);
      buffer.writeByte(ENVELOPE_REQUEST);
      try {
         header.write(data);
         req.write(data);
         buffer.setInt(0, buffer.writerIndex() - 4); // go back and write message length
         log(buffer);
         channel.writeAndFlush(buffer);
      } catch (IOException e) {
         ReferenceCountUtil.release(buffer);
         logger.error(e.getMessage(), e);
         async.setResponse(null, 0); // FIXME
      }
      return async;
   }

   private void log(ByteBuf buf) {
      System.out.print("BUF = ");
      for (int i = 0; i < buf.readableBytes(); i++) {
         byte b = buf.getByte(i);
         System.out.print(b + " ");
      }
      System.out.println();
   }

   // FIXME: Need a Protocol class
   public Structure makeStruct(int structId) {
      switch (structId) {
         case RegisterRequest.STRUCT_ID:
            return new RegisterRequest();
         case RegisterResponse.STRUCT_ID:
            return new RegisterResponse();
      }
      return null;
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
   }

   private void sendPing() {
      final ByteBuf buffer = channel.alloc().buffer(5);
      buffer.writeInt(1);
      buffer.writeByte(ENVELOPE_PING);
      channel.writeAndFlush(buffer);
   }

   private void sendPong() {
      final ByteBuf buffer = channel.alloc().buffer(5);
      buffer.writeInt(1);
      buffer.writeByte(ENVELOPE_PONG);
      channel.writeAndFlush(buffer);
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

   public void checkHealth() {
      if (isConnected()) {
         final long now = System.currentTimeMillis();
         if (now - lastHeardFrom.get() > 5000) {
            sendPing();
         } else if (now > 10000) {
            logger.warn("{} Timeout");
            close();
         }
      }
   }

   private void scheduleHealthCheck() {
      if (isConnected()) {
         dispatcher.dispatch(1, TimeUnit.SECONDS, new Runnable() {
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
