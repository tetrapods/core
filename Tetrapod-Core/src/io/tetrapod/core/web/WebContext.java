package io.tetrapod.core.web;

import static io.netty.buffer.Unpooled.*;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.tetrapod.protocol.core.Core.UNADDRESSED;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.util.CharsetUtil;
import io.tetrapod.core.Session;
import io.tetrapod.core.json.JSONObject;
import io.tetrapod.protocol.core.RequestHeader;

import java.io.IOException;
import java.util.*;

class WebContext {
   
   public static ByteBuf makeByteBufResult(Object result) {
      if (result instanceof ByteBuf) return (ByteBuf)result;
      if (result instanceof byte[]) return wrappedBuffer((byte[])result);
      return copiedBuffer(result.toString(), CharsetUtil.UTF_8);
   }
   
   private JSONObject requestParameters;
   private String requestPath;

   public WebContext(HttpRequest request) throws IOException {
      parseRequestParameters(request);
   }
   
   public WebContext(JSONObject json) throws IOException {
      this.requestParameters = json;
      this.requestPath = json.optString("path", "/unknown");
   }
   
   public RequestHeader makeRequestHeader(Session s) {
      RequestHeader header = new RequestHeader();
      header.requestId = requestParameters.optInt("requestId", -1);
      if (header.requestId < 0)
         return null;
      header.toId = requestParameters.optInt("toId", UNADDRESSED);
      header.contractId = requestParameters.optInt("contractId", 0);
      header.structId = requestParameters.optInt("structId", 0);
      header.fromId = s.getTheirEntityId();
      header.fromType = s.getTheirEntityType();
      return header;
   }

   public JSONObject getRequestParams() {
      return requestParameters;
   }
   
   public String getRequestPath() {
      return requestPath;
   }

   private void parseRequestParameters(HttpRequest request) throws IOException {
      QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
      this.requestPath = queryStringDecoder.path();
      this.requestParameters = new JSONObject();
      Map<String,List<String>> reqs = queryStringDecoder.parameters();
      for (String k : reqs.keySet())
         for (String v : reqs.get(k))
            requestParameters.accumulate(k, v);

      // Add POST parameters
      if (request.getMethod() != POST)
         return;
      HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(false), request);
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
         // Exception when the body is fully decoded, even if there
         // is still data
      }
      decoder.destroy();
   }


}
