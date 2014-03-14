package io.tetrapod.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.ReferenceCountUtil;

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
   public void channelRead(ChannelHandlerContext ctx, Object msg) {
      lastHeardFrom.set(System.currentTimeMillis());
      try {
         final ByteBuf in = (ByteBuf) msg;
         final byte envelope = in.readByte();
         if (needsHandshake()) {
            if (envelope == ENVELOPE_HANDSHAKE) {
               if (readHandshake(in)) {
                  fireSessionStartEvent();
               }
            } else {
               close();
            }
         } else {
            read(in, envelope);
         }
      } finally {
         ReferenceCountUtil.release(msg);
      }
   }

   private void read(final ByteBuf in, final byte envelope) {
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

   private boolean readHandshake(ByteBuf in) {
      int theirVersion = in.readInt();
      @SuppressWarnings("unused")
      int theirOptions = in.readInt();
      if (theirVersion != WIRE_VERSION) {
         logger.warn("{} handshake version does not match {}", this, theirVersion);
         close();
         return false;
      }
      logger.info("{} handshake succeeded", this);
      synchronized (this) {
         needsHandshake = false;
      }
      return true;
   }

   private void readResponse(ByteBuf in) {
      // TODO
   }

   private void readRequest(ByteBuf in) {
      // TODO
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
