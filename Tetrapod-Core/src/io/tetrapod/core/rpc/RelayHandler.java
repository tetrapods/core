package io.tetrapod.core.rpc;

import io.netty.buffer.ByteBuf;
import io.tetrapod.core.Session;
import io.tetrapod.protocol.core.RequestHeader;

public interface RelayHandler {
   public void relayRequest(final RequestHeader header, final ByteBuf in, final Session fromSession);
}
