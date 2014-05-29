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
package io.tetrapod.core.flashpolicy;

import io.netty.buffer.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

import java.util.List;

/**
 * @author <a href="http://www.waywardmonkeys.com/">Bruce Mitchener</a>
 */
/**
 * Modified to work w netty 4
 */
public class FlashPolicyServerDecoder extends ReplayingDecoder<Void> {
    // We don't check for the trailing NULL to make telnet-based debugging easier.
    private final static ByteBuf REQUEST = Unpooled.wrappedBuffer("<policy-file-request/>".getBytes());

   @Override
   protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
      ByteBuf data = in.readBytes(REQUEST.readableBytes());
      
      if (data.equals(REQUEST)) {
         out.add(Boolean.TRUE);
      } else {
         ctx.close();
      }
      data.release();
   }
}
