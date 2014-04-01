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
import io.tetrapod.protocol.core.*;

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
      this.requestPath = json.optString("_uri", "/unknown");
   }
   
   public RequestHeader makeRequestHeader(Session s, WebRoutes routes) {
      RequestHeader header = new RequestHeader();
      header.requestId = requestParameters.optInt("_requestId", -1);
      header.toId = requestParameters.optInt("_toId", UNADDRESSED);
      header.fromId = s.getTheirEntityId();
      header.fromType = s.getTheirEntityType();
      WebRoute route = routes.findRoute(requestPath);
      if (route != null) {
         header.contractId = route.contractId;
         header.structId = route.structId;
      } else {
         // TODO: should we allow this? this lets the javascript interface 
         //       call any request instead of just ones mapped as web routes
         header.contractId = requestParameters.optInt("_contractId", -1);
         header.structId = requestParameters.optInt("_structId", -1);
      }
      if (header.requestId < 0 || header.contractId < 0 || header.structId < 0)
         return null;
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