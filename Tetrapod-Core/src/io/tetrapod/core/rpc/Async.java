package io.tetrapod.core.rpc;

import org.slf4j.*;

public class Async<R extends Request<T>, T extends Response> {
   public static final Logger logger = LoggerFactory.getLogger(Async.class);

   public final R             request;

   public T                   response;
   public Handler<T>          handler;

   public Async(R request) {
      this.request = request;
   }

   public interface Handler<T> {
      public void onResponse(T res, int errorCode);
   }

   public synchronized void setHandler(Handler<T> handler) {
      this.handler = handler;
      if (response != null) {
         handler.onResponse(response, 0);
      }
   }

   public synchronized void setResponse(T res, int errorCode) {
      response = res;
      if (handler != null) {
         handler.onResponse(res, errorCode);
      }
   }

}
