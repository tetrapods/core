package io.tetrapod.core.rpc;

import java.util.ArrayList;

import org.slf4j.*;

import io.tetrapod.core.rpc.Async.IResponseHandlerErr;
import io.tetrapod.core.rpc.ErrorResponseException;
import io.tetrapod.protocol.core.CoreContract;

/**
 * Manages running a sequence of tasks. Each task can either call proceed() or reject() on
 * this object. Control then passes to the next handler that handles normal execution
 * (proceed) or errors (reject).
 * <p>
 * In general this class solves the problem where you want to go: reqA then (reqB or reqC)
 * then reqD ... If you nest PendingResponseHandlers then the handlers for reqB abd reqC
 * have to duplicate all the logic for reqD onwards. Other solutions are turning requests
 * into blocking requests, or basically wrapping the call chain up in a small state
 * machine. The hope is this class leads to more maintainable and performant code than the
 * other cases.
 * <p>
 * Besides addressing the nesting issue, this class has some convenience built in:
 * <li>optionally propogates errors from a number of calls to a single handler
 * <li>only calls response handlers if the call was successful
 * <li>allows response handlers to throw checked exceptions (which get logged and turned
 * into ERROR_UNKNOWN)
 * <li>allows response handlers to throw ErrorResponseExceptions which turn into proper
 * errors
 * <li>each runnable is invoked in a synchronized block, which provides a memory
 * consistency barrier for the next call
 */
public class AsyncSequence {
   private static final Logger logger = LoggerFactory.getLogger(AsyncSequence.class);

   public static interface NormalRunnable {
      void run() throws Exception;
   }

   public static interface ErrRunnable {
      void run(int err);
   }

   private final ArrayList<Object> sequence = new ArrayList<>();
   private int                     ix       = 0;

   /**
    * Adds the runnable to the sequence, and immediately invokes it if it's the given
    * runnables turn. This runnable will only be invoked on proceeds.
    */
   public synchronized AsyncSequence then(NormalRunnable r) {
      boolean autoRun = ix == sequence.size();
      sequence.add(r);
      if (autoRun) {
         ix--;
         proceed();
      }
      return this;
   }

   /**
    * Adds an error handling runnable to the sequence, and immediately invokes it if it's
    * the given runnables turn. This runnable will only be invoked on errors.
    */
   public synchronized AsyncSequence onError(ErrRunnable r) {
      sequence.add(r);
      return this;
   }

   /**
    * Rejects the sequence of runnables with the given error code, will cause the next
    * error runnable to be invoked.
    */
   public synchronized void reject(int errorCode) {
      for (ix++; ix < sequence.size(); ix++) {
         Object obj = sequence.get(ix);
         if (obj instanceof ErrRunnable) {
            try {
               ((ErrRunnable) obj).run(errorCode);
            } catch (ErrorResponseException e) {
               reject(e.errorCode);
            } catch (Exception e) {
               logger.error(e.getMessage(), e);
               reject(CoreContract.ERROR_UNKNOWN);
            }
            return;
         }
      }
   }

   /**
    * Goes on to the next in the sequence of events with the given error code, will cause
    * the next normal runnable to be invoked.
    */
   public synchronized void proceed() {
      for (ix++; ix < sequence.size(); ix++) {
         Object obj = sequence.get(ix);
         if (obj instanceof NormalRunnable) {
            try {
               ((NormalRunnable) obj).run();
            } catch (ErrorResponseException e) {
               reject(e.errorCode);
            } catch (Exception e) {
               logger.error(e.getMessage(), e);
               reject(CoreContract.ERROR_UNKNOWN);
            }
            return;
         }
      }
   }

   public ResponseHandler responseHandlerFor(IResponseHandlerErr handler) {
      return new SequenceResponseHandler(this, handler);
   }

   private static class SequenceResponseHandler extends ResponseHandler {
      private final AsyncSequence       seq;
      private final IResponseHandlerErr handler;

      public SequenceResponseHandler(AsyncSequence seq, IResponseHandlerErr handler) {
         this.seq = seq;
         this.handler = handler;
      }

      public void onResponse(Response res) {
         if (res.isSuccess()) {
            try {
               handler.onResponse(res);
            } catch (ErrorResponseException e) {
               seq.reject(e.errorCode);
            } catch (Exception e) {
               logger.error(e.getMessage(), e);
               seq.reject(CoreContract.ERROR_UNKNOWN);
            }
         } else {
            seq.reject(res.errorCode());
         }
      }
   }

}
