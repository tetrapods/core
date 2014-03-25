package io.tetrapod.core.web;

import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.*;

public class WebSessionFactory implements SessionFactory {

   public WebSessionFactory(Session.Helper helper, String contentRoot, boolean webSockets) {
      this.helper = helper;
      this.contentRoot = contentRoot;
      this.webSockets = webSockets;
   }
   
   boolean webSockets = false;
   String contentRoot;
   Session.Helper helper;

   @Override
   public Session makeSession(SocketChannel ch) {
      if (webSockets)
         return new WebSocketSession(ch, helper, contentRoot);
      else
         return new RESTSession(ch, helper, contentRoot);
   }

}
