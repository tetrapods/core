/*
 * Copyright 2010 Bruce Mitchener.
 *
 * Bruce Mitchener licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
/**
 * Modified to work with tetrapod Server class
 */
package io.tetrapod.core.flashpolicy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.tetrapod.core.*;
import io.tetrapod.core.utils.Util;


public class FlashPolicyServer extends Server {  
   
   
   public FlashPolicyServer(Dispatcher dispatcher) {
      super(Util.getProperty("tetrapod.flashpolicy.port", 8843), null, dispatcher);
   }
   
   @Override
   protected void setOptions(ServerBootstrap sb) {
       sb.childOption(ChannelOption.TCP_NODELAY, true);
       sb.childOption(ChannelOption.SO_KEEPALIVE, true);
   }
   
   @Override
   public void initChannel(SocketChannel ch) throws Exception {
      ChannelPipeline pipeline = ch.pipeline();
      pipeline.addLast("timeout", new ReadTimeoutHandler(5));
      pipeline.addLast("decoder", new FlashPolicyServerDecoder());
      pipeline.addLast("handler", new FlashPolicyServerHandler());
   }
}
