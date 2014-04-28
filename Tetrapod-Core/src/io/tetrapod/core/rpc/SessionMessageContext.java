package io.tetrapod.core.rpc;

import io.tetrapod.core.Session;
import io.tetrapod.protocol.core.*;

public class SessionMessageContext extends MessageContext {

   public final Session       session;

   public SessionMessageContext(MessageHeader header, Session session) {
      super(header);
      this.session = session;
   }
}
