package io.tetrapod.core.web;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.tetrapod.protocol.core.Core.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import io.tetrapod.core.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.core.serialize.datasources.*;
import io.tetrapod.protocol.core.RequestHeader;

import java.io.*;

import org.slf4j.*;

class WebHttpSession extends Session {

   private static final Logger logger = LoggerFactory.getLogger(WebHttpSession.class);
   
   private boolean isKeepAlive;

   public WebHttpSession(SocketChannel ch, Session.Helper helper, String contentRoot) {
      super(ch, helper);
      
      // Uncomment the following lines if you want HTTPS
      // SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
      // engine.setUseClientMode(false);
      // ch.pipeline().addLast("ssl", new SslHandler(engine));
      
      ch.pipeline().addLast("decoder", new HttpRequestDecoder());
      ch.pipeline().addLast("aggregator", new HttpObjectAggregator(65536));
      ch.pipeline().addLast("encoder", new HttpResponseEncoder());
      ch.pipeline().addLast("api", this);
      if (contentRoot != null) {
         WebStaticFileHandler sfh = new WebStaticFileHandler(false, new File(contentRoot));
         ch.pipeline().addLast("files", sfh);
      }
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
      if (!(obj instanceof HttpRequest) || relayHandler==null) {
         ctx.fireChannelRead(obj);
         return;
      }
      WebContext context = new WebContext((HttpRequest)obj);
      RequestHeader header = context.makeRequestHeader(this, relayHandler.getWebRoutes());
      if (header == null) {
         ctx.fireChannelRead(obj);
         return;
      }
      isKeepAlive =  HttpHeaders.isKeepAlive((HttpRequest)obj);
      ReferenceCountUtil.release(obj);
      
      try {
         if ((header.toId == UNADDRESSED && header.contractId == myContractId) || header.toId == myId) {
            final Request req = (Request) StructureFactory.make(header.contractId, header.structId);
            if (req != null) {
               req.read(new WebJSONDataSource(context.getRequestParams(), req.tagWebNames()));
               dispatchRequest(header, req);
            } else {
               logger.warn("Could not find request structure {}", header.structId);
               sendResponse(new Error(ERROR_SERIALIZATION), header.requestId);
            }
         } else if (relayHandler != null) {
            // TODO implement
            // relayRequest(header, jo);
         }

      } catch (IOException e) {
      }
   }
   

   @Override
   public Async sendRequest(Request req, int toId, byte timeoutSeconds) {
      logger.error("can't send requests to clients {}", req.dump());
      return null;
   }

   @Override
   protected void sendResponse(Response res, int requestId) {
      JSONDataSource jds = new WebJSONDataSource(res.tagWebNames());
      try {
         res.write(jds);
      } catch (IOException e) {
         logger.error(e.getMessage(), e);
         return;
      }
      jds.getJSON().put("num", requestId);
      jds.getJSON().put("structId", res.getStructId());
      jds.getJSON().put("contractId", res.getContractId());

      ByteBuf buf = WebContext.makeByteBufResult(jds.getJSON().toString(3));
      FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, buf);
      httpResponse.headers().set(CONTENT_TYPE, "text/json");
      httpResponse.headers().set(CONTENT_LENGTH, httpResponse.content().readableBytes());
      if (isKeepAlive) {
         httpResponse.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
      } else {
         httpResponse.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
      }
      channel.writeAndFlush(httpResponse);
   }

   @Override
   public void sendMessage(Message msg, int toEntityId, int topicId) {
      // TODO
   }

   

}
