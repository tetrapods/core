package io.tetrapod.core;

import org.slf4j.*;

import io.netty.channel.socket.SocketChannel;

public class WireSessionFactory implements SessionFactory {
   public static final Logger     logger = LoggerFactory.getLogger(WireSessionFactory.class);

   private final byte             theirType;
   private final DefaultService   service;
   private final Session.Listener listener;

   public WireSessionFactory(DefaultService service, byte theirType, Session.Listener listener) {
      this.theirType = theirType;
      this.service = service;
      this.listener = listener;
   }

   /**
    * Session factory for our sessions from clients and services
    */
   @Override
   public Session makeSession(SocketChannel ch) {
      final Session ses = new WireSession(ch, service);
      ses.setMyEntityId(service.getEntityId());
      ses.setMyEntityType(service.getEntityType());
      ses.setTheirEntityType(theirType);
      if (listener != null) {
         ses.addSessionListener(listener);
      }
      return ses;
   }
}
