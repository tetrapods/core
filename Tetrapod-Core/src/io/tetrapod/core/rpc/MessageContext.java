package io.tetrapod.core.rpc;

import io.tetrapod.core.Session;
import io.tetrapod.protocol.core.*;

public class MessageContext {

   public final MessageHeader header;
   public final Session       session;

   public MessageContext(MessageHeader header, Session session) {
      this.header = header;
      this.session = session;
   }
}
