package io.tetrapod.core.rpc;

import io.tetrapod.protocol.core.MessageHeader;

public class MessageContext {

   public final MessageHeader header;
   
   public MessageContext(MessageHeader header) {
      this.header = header;
   }

}
