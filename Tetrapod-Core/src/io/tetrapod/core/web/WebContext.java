package io.tetrapod.core.web;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.tetrapod.protocol.core.Core.UNADDRESSED;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.util.CharsetUtil;
import io.tetrapod.core.Session;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.protocol.core.RequestHeader;
import io.tetrapod.protocol.core.WebRoute;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class WebContext {

   public static ByteBuf makeByteBufResult(Object result) {
      if (result instanceof ByteBuf)
         return (ByteBuf) result;
      if (result instanceof byte[])
         return wrappedBuffer((byte[]) result);
      return copiedBuffer(result.toString(), CharsetUtil.UTF_8);
   }

   private JSONObject requestParameters;
   private String     requestPath;

   public WebContext(FullHttpRequest request) throws IOException {
      parseRequestParameters(request);
   }

   public WebContext(JSONObject json) throws IOException {
      this.requestParameters = json;
      this.requestPath = json.optString("_uri", "/unknown");
   }

   public RequestHeader makeRequestHeader(Session s, WebRoute route) {
      return makeRequestHeader(s, route, requestParameters);
   }

   public static RequestHeader makeRequestHeader(Session s, WebRoute route, JSONObject params) {
      RequestHeader header = new RequestHeader();

      header.toId = params.optInt("_toId", UNADDRESSED);
      header.fromId = s.getTheirEntityId();
      header.fromType = s.getTheirEntityType();
      if (route == null) {
         // route is null for web socket & poller calls
         header.requestId = params.optInt("_requestId", -1);
         header.contractId = params.optInt("_contractId", -1);
         header.structId = params.optInt("_structId", -1);
      } else {
         header.contractId = route.contractId;
         header.structId = route.structId;
      }
      if (header.requestId < 0 || header.contractId < 0 || header.structId < 0) {
         return null;
      }
      header.timeout = (byte) 30;
      return header;
   }

   public JSONObject getRequestParams() {
      return requestParameters;
   }

   public String getRequestPath() {
      return requestPath;
   }

   private void parseRequestParameters(FullHttpRequest request) throws IOException {
      final QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
      this.requestPath = queryStringDecoder.path();
      this.requestParameters = new JSONObject();
      final Map<String, List<String>> reqs = queryStringDecoder.parameters();
      for (String k : reqs.keySet())
         for (String v : reqs.get(k))
            requestParameters.accumulate(k, v);

      // Add POST parameters
      if (request.getMethod() != POST)
         return;

      final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(false), request);
      try {
         while (decoder.hasNext()) {
            InterfaceHttpData httpData = decoder.next();

            if (httpData.getHttpDataType() == HttpDataType.Attribute) {
               Attribute attribute = (Attribute) httpData;
               requestParameters.accumulate(attribute.getName(), attribute.getValue());
               attribute.release();
            }
         }
      } catch (HttpPostRequestDecoder.EndOfDataDecoderException ex) {
         // Exception when the body is fully decoded, even if there is still data
      } finally {
         decoder.destroy();
      }
   }

}
