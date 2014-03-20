package io.tetrapod.core.rpc;

import io.tetrapod.core.Session;
import io.tetrapod.core.protocol.RequestHeader;

public class RequestContext {

   public final RequestHeader header;
   public final Session       session;

   public RequestContext(RequestHeader header, Session session) {
      this.header = header;
      this.session = session;
   }
}
