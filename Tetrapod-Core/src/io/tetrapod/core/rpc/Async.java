package io.tetrapod.core.rpc;

import io.tetrapod.core.Session;
import io.tetrapod.protocol.core.CoreContract;
import io.tetrapod.protocol.core.RequestHeader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Async {
   public static final Logger logger   = LoggerFactory.getLogger(Async.class);

   public final long          sendTime = System.currentTimeMillis();
   public final RequestHeader header;
   public final Request       request;
   public final Session       session;

   private Response           response;
   private ResponseHandler    handler;

   public Async(Request request, RequestHeader header, Session session) {
      this.request = request;
      this.header = header;
      this.session = session;
   }

   public Async(Request request, RequestHeader header, Session session, ResponseHandler handler) {
      this(request, header, session);
      this.handler = handler;
   }

   public synchronized boolean hasHandler() {
      return handler != null;
   }

   public interface IResponseHandler {
      public void onResponse(Response res);
   }

   public interface IResponseHandlerErr {
      public void onResponse(Response res) throws Exception;
   }

   public synchronized void handle(IResponseHandler handler) {
      handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            try {
               handler.onResponse(res);
            } catch (AsyncSequenceRejectException e) {
               // ignore, sequence reject handler called
            }
         }
      });
   }

   public synchronized void handle(AsyncSequence seq) {
      handle(seq.responseHandlerFor(resp -> {
         seq.putValue("response", resp);
         seq.proceed();
      }));
   }

   public synchronized void handle(AsyncSequence seq, IResponseHandlerErr handler) {
      handle(seq.responseHandlerFor(handler));
   }

   public synchronized void handle(ResponseHandler handler) {
      this.handler = handler;
      if (response != null) {
         handler.fireResponse(response, header);
      }
   }

   public synchronized void setResponse(int errorCode) {
      setResponse(new Error(errorCode));
   }

   public synchronized void setResponse(Response res) {
      assert (res != Response.PENDING);
      response = res;
      if (handler != null) {
         try {
            handler.fireResponse(res, header);
         } catch (Throwable e) {
            logger.error(e.getMessage(), e);
         }
      }
      notifyAll();
   }

   public boolean isTimedout() {
      return header == null ? false : System.currentTimeMillis() - sendTime > header.timeout * 1000;
   }

   public synchronized Response waitForResponse() {
      while (response == null) {
         try {
            wait(1000);
         } catch (InterruptedException e) {}
         if (isTimedout())
            setResponse(CoreContract.ERROR_TIMEOUT);
      }
      return response;
   }

   public synchronized int getErrorCode() {
      return response == null ? -1 : response.errorCode();
   }

   public void log() {
      handle(ResponseHandler.LOGGER);
   }

}
