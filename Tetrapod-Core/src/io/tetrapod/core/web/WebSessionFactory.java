package io.tetrapod.core.web;

import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.*;

public class WebSessionFactory implements SessionFactory {

   public WebSessionFactory(Session.Helper helper, Session.RelayHandler relay, String contentRoot, boolean webSockets) {
      this.helper = helper;
      this.contentRoot = contentRoot;
      this.webSockets = webSockets;
      this.relay = relay;
   }
   
   boolean webSockets = false;
   String contentRoot;
   Session.Helper helper;
   Session.RelayHandler relay;

   @Override
   public Session makeSession(SocketChannel ch) {
      Session s = webSockets ? new WebSocketSession(ch, helper, contentRoot) : new WebHttpSession(ch, helper, contentRoot);
      s.setRelayHandler(relay);;
      return s;
   }

}
