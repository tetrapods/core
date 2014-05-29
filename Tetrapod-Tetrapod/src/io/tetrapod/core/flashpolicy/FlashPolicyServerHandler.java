/*
 * Copyright 2010 Bruce Mitchener.
 * 
 * Bruce Mitchener licenses this file to you under the Apache License, version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
/**
 * Modified to work w netty 4, read file from disk based on properties
 */
package io.tetrapod.core.flashpolicy;

import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.handler.timeout.ReadTimeoutException;
import io.tetrapod.core.utils.Util;

import java.io.*;

import org.slf4j.*;

public class FlashPolicyServerHandler extends SimpleChannelInboundHandler<Boolean> {

   public static final Logger  logger = LoggerFactory.getLogger(FlashPolicyServerHandler.class);

   private static final ByteBuf POLICY_FILE;
   static {
      byte[] b = null;
      try {
         b = Util.readFile(new File(Util.getProperty("flash.policy", "cfg/debug_flash_policy.xml")));
      } catch (IOException e) {
         b = "ERR: no policy file\n".getBytes();
         logger.error("could not read policy file", e);
      }
      POLICY_FILE = Unpooled.wrappedBuffer(b);
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      if (cause instanceof ReadTimeoutException) {
         logger.warn("Flash policy connection timed out.");
      } else {
         logger.error("unexcpeted flash policy errpr", cause);
      }
      ctx.close();
   }

   @Override
   protected void channelRead0(ChannelHandlerContext ctx, Boolean msg) throws Exception {
      assert msg == Boolean.TRUE;
      POLICY_FILE.retain();
      ctx.writeAndFlush(POLICY_FILE).addListener(ChannelFutureListener.CLOSE);
   }

}
