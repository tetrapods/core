package io.tetrapod.core;

import io.netty.channel.socket.SocketChannel;

public interface SessionFactory {

   Session makeSession(SocketChannel ch);

}
