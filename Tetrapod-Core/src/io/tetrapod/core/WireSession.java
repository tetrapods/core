package io.tetrapod.core;

import static io.tetrapod.protocol.core.Core.*;
import static io.tetrapod.protocol.core.CoreContract.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.ReferenceCountUtil;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.DataSource;
import io.tetrapod.core.serialize.datasources.ByteBufDataSource;
import io.tetrapod.protocol.core.*;

import java.io.IOException;

import org.slf4j.*;

/**
 * Manages a session between two tetrapods
 */
public class WireSession extends Session {

   private static final Logger logger         = LoggerFactory.getLogger(WireSession.class);

   private static final int    WIRE_VERSION   = 1;
   private static final int    WIRE_OPTIONS   = 0x00000000;

   private boolean             needsHandshake = true;

   public WireSession(SocketChannel channel, WireSession.Helper helper) {
      super(channel, helper);
      channel.pipeline().addLast(new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 0));
      channel.pipeline().addLast(this);
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
         case ENVELOPE_BROADCAST:
            readMessage(in, envelope == ENVELOPE_BROADCAST);
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
      boolean logged = false;
      header.read(reader);

      final Async async = pendingRequests.remove(header.requestId);
      if (async != null) {
         // Dispatches response to ourselves if we sent the request (fromId == myId) or 
         // if fromId is UNADRESSED this handles the edge case where we are registering 
         // ourselves and so did not yet have an entityId           
         if ((async.header.fromId == myId || async.header.fromId == Core.UNADDRESSED) && async.request != null) {
            final Response res = (Response) StructureFactory.make(header.contractId, header.structId);
            if (res != null) {
               res.read(reader);
               if (!commsLogIgnore(header.structId))
                  logged = commsLog("%s  [%d] <- %s", this, header.requestId, res.dump());
               getDispatcher().dispatch(new Runnable() {
                  public void run() {
                     async.setResponse(res);
                  }
               });
            } else {
               logger.warn("{} Could not find response structure {}", this, header.structId);
            }
         } else if (relayHandler != null) {

            // HACK: Expensive, for better debug logs. 
            Response res = null;
            //            if (logger.isDebugEnabled() && header.structId == 1) {
            //               res = (Response) StructureFactory.make(async.header.contractId, header.structId);
            //               if (res != null) {
            //                  int mark = in.readerIndex();
            //                  res.read(reader);
            //                  in.readerIndex(mark);
            //               }
            //            }

            if (!commsLogIgnore(header.structId))
               logged = commsLog("%s  [%d] <- Response.%d %s", this, header.requestId, header.structId, res == null ? "" : res.dump());
            relayResponse(header, async, in);
         }
      } else {
         logger.warn("{} Could not find pending request for {}", this, header.dump());
      }

      if (!logged && !commsLogIgnore(header.structId))
         logged = commsLog("%s  [%d] <- Response.%d", this, header.requestId, header.structId);
   }

   private void readRequest(final ByteBuf in) throws IOException {
      DataSource reader = new ByteBufDataSource(in);
      final RequestHeader header = new RequestHeader();
      boolean logged = true;
      header.read(reader);

      // set/clobber with known details, unless it's from a trusted tetrapod
      if (theirType != TYPE_TETRAPOD) {
         header.fromId = theirId;
         header.fromType = theirType;
      }

      if ((header.toId == DIRECT) || header.toId == myId) {
         final Request req = (Request) StructureFactory.make(header.contractId, header.structId);
         if (req != null) {
            req.read(reader);
            if (!commsLogIgnore(req))
               logged = commsLog("%s  [%d] <- %s", this, header.requestId, req.dump());
            dispatchRequest(header, req);
         } else {
            logger.warn("Could not find request structure {}", header.structId);
            sendResponse(new Error(ERROR_SERIALIZATION), header.requestId);
         }
      } else if (relayHandler != null) {
         if (!commsLogIgnore(header.structId))
            logged = commsLog("%s  [%d] <- Request.%d", this, header.requestId, header.structId);
         relayRequest(header, in);
      }

      if (!logged && !commsLogIgnore(header.structId))
         logged = commsLog("%s  [%d] <- Request.%d", this, header.requestId, header.structId);
   }

   private void readMessage(ByteBuf in, boolean isBroadcast) throws IOException {
      final ByteBufDataSource reader = new ByteBufDataSource(in);
      final MessageHeader header = new MessageHeader();

      header.read(reader);
      if (theirType != TYPE_TETRAPOD) {
         // fromId MUST be their id, unless it's a tetrapod session, which could be relaying
         header.fromId = theirId;
      }

      if (!commsLogIgnore(header.structId))
         commsLog("%s  [M] <- T%d.Message:%d %s", this, header.topicId, header.structId, getNameFor(header));
      if (relayHandler == null || header.toId == myId || (header.toId == UNADDRESSED && header.topicId == UNADDRESSED)) {
         dispatchMessage(header, reader);
      } else {
         relayHandler.relayMessage(header, in, isBroadcast);
      }
   }

   protected void dispatchMessage(final MessageHeader header, final ByteBufDataSource reader) throws IOException {
      final Message msg = (Message) StructureFactory.make(header.contractId, header.structId);
      if (msg != null) {
         msg.read(reader);
         dispatchMessage(header, msg);
      } else {
         logger.warn("Could not find message structure {}", header.structId);
      }
   }

   @Override
   protected ByteBuf makeFrame(Structure header, Structure payload, byte envelope) {
      return makeFrame(header, payload, envelope, channel.alloc().buffer(128));
   }

   private ByteBuf makeFrame(Structure header, Structure payload, byte envelope, ByteBuf buffer) {
      final ByteBufDataSource data = new ByteBufDataSource(buffer);
      buffer.writeInt(0);
      buffer.writeByte(envelope);
      try {
         header.write(data);
         payload.write(data);
         buffer.setInt(0, buffer.writerIndex() - 4); // go back and write message length, now that we know it
         return buffer;
      } catch (IOException e) {
         ReferenceCountUtil.release(buffer);
         logger.error(e.getMessage(), e);
         return null;
      }
   }

   @Override
   public void channelActive(final ChannelHandlerContext ctx) throws Exception {
      writeHandshake();
      scheduleHealthCheck();
   }

   @Override
   public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      fireSessionStopEvent();
      cancelAllPendingRequests();
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
      writeFrame(buffer);
   }

   @Override
   protected void sendPing() {
      final ByteBuf buffer = channel.alloc().buffer(5);
      buffer.writeInt(1);
      buffer.writeByte(ENVELOPE_PING);
      writeFrame(buffer);
   }

   @Override
   protected void sendPong() {
      final ByteBuf buffer = channel.alloc().buffer(5);
      buffer.writeInt(1);
      buffer.writeByte(ENVELOPE_PONG);
      writeFrame(buffer);
   }

   // /////////////////////////////////// RELAY /////////////////////////////////////

   @Override
   protected Object makeFrame(Structure header, ByteBuf payload, byte envelope) {
      // OPTIMIZE: Find a way to relay without the extra allocation & copy
      ByteBuf buffer = channel.alloc().buffer(32 + payload.readableBytes());
      ByteBufDataSource data = new ByteBufDataSource(buffer);
      try {
         buffer.writeInt(0);
         buffer.writeByte(envelope);
         header.write(data);
         buffer.writeBytes(payload);
         buffer.setInt(0, buffer.writerIndex() - 4); // Write message length, now that we know it
         return buffer;
      } catch (IOException e) {
         ReferenceCountUtil.release(buffer);
         logger.error(e.getMessage(), e);
         return null;
      }
   }

   private void relayRequest(final RequestHeader header, final ByteBuf in) {
      final Session ses = relayHandler.getRelaySession(header.toId, header.contractId);
      if (ses != null) {
         ses.sendRelayedRequest(header, in, this);
      } else {
         logger.warn("Could not find a relay session for {}", header.toId);
         sendResponse(new Error(ERROR_SERVICE_UNAVAILABLE), header.requestId);
      }
   }

   private void relayResponse(ResponseHeader header, Async async, ByteBuf in) {
      header.requestId = async.header.requestId;
      async.session.sendRelayedResponse(header, in);
   }

   public static String dumpBuffer(ByteBuf buf) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < buf.writerIndex(); i++) {
         sb.append(buf.getByte(i));
         sb.append(' ');
      }
      return sb.toString();
   }

   @Override
   public String getPeerHostname() {
      return channel.remoteAddress().getHostString();
   }

}
