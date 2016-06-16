package io.tetrapod.core.rpc;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import io.tetrapod.core.utils.Util;
import org.slf4j.*;

import io.tetrapod.core.rpc.Async.IResponseHandlerErr;
import io.tetrapod.protocol.core.CoreContract;

/**
 * Manages running a sequence of tasks. Each task can either call proceed() or reject() on
 * this object. Control then passes to the next handler that handles normal execution
 * (proceed) or errors (reject).
 * <p>
 * In general this class solves the problem where you want to go: reqA then (reqB or reqC)
 * then reqD ... If you nest PendingResponseHandlers then the handlers for reqB and reqC
 * have to duplicate all the logic for reqD onwards. Other solutions are turning requests
 * into blocking requests, or basically wrapping the call chain up in a small state
 * machine. The hope is this class leads to more maintainable and performant code than the
 * other cases.
 * <p>
 * Besides addressing the nesting issue, this class has some convenience built in:
 * <li>optionally propagates errors from a number of calls to a single handler
 * <li>only calls response handlers if the call was successful
 * <li>allows response handlers to throw checked exceptions (which get logged and turned
 * into ERROR_UNKNOWN)
 * <li>allows response handlers to throw ErrorResponseExceptions which turn into proper
 * errors
 * <li>each runnable is invoked in a synchronized block, which provides a memory
 * consistency barrier for the next call
 */
public class AsyncSequence {
   private static final Logger       logger = LoggerFactory.getLogger(AsyncSequence.class);

   public static final AsyncSequence EMPTY  = new AsyncSequence();

   public static interface NormalRunnable {
      void run(AsyncSequence seq) throws Exception;
   }

   public static interface ErrRunnable {
      void run(int err);
   }

   public static interface ErrRunnableWithSeq {
      void run(AsyncSequence seq, int err);
   }

   public static interface ErrRunnableWithException {
      void run(AsyncSequence seq, int err, Exception ex);
   }

   /**
    * Adds the runnable to the sequence, and immediately invokes it if it's the given
    * runnable's turn. This runnable will only be invoked on proceeds.
    */
   public static AsyncSequence with(NormalRunnable r) {
      return new AsyncSequence().add(r);
   }

   private final ArrayList<Object>                 sequence       = new ArrayList<>();
   private final ConcurrentHashMap<String, Object> sequenceValues = new ConcurrentHashMap<>();
   private int                                     ix             = 0;
   private volatile int                            errorCode      = 0;
   private volatile Exception                      errorException = null;

   /**
    * Adds the runnable to the sequence, and immediately invokes it if it's the given
    * runnable's turn. This runnable will only be invoked on proceeds.
    */
   public synchronized AsyncSequence then(NormalRunnable r) {
      return add(r);
   }

   /**
    * Adds an error handling runnable to the sequence, and immediately invokes it if it's
    * the given runnable's turn. This runnable will only be invoked on errors.
    */
   public synchronized AsyncSequence onError(ErrRunnable r) {
      return add(r);
   }

   /**
    * Adds an error handling runnable to the sequence, and immediately invokes it if it's
    * the given runnable's turn. This runnable will only be invoked on errors.
    */
   public synchronized AsyncSequence onError(ErrRunnableWithSeq r) {
      return add(r);
   }

   /**
    * Adds an error handling runnable to the sequence, and immediately invokes it if it's
    * the given runnable's turn. This runnable will only be invoked on errors.
    */
   public synchronized AsyncSequence onError(ErrRunnableWithException r) {
      return add(r);
   }

   private AsyncSequence add(Object r) {
      sequence.add(r);
      if (ix == sequence.size() - 1)
         autorun();
      return this;
   }

   private void autorun() {
      ix--;
      if (errorCode > 0) {
         int errCode = errorCode;
         Exception errEx = errorException;
         this.errorCode = 0;
         this.errorException = null;
         doReject(errCode, errEx);
      } else {
         proceed();
      }
   }

   /**
    * Halts the current thread, rejects the sequence of runnables with the given error
    * code, and will cause the next error runnable to be invoked with this error code and
    * null as the exception.
    */
   public synchronized void reject(int errorCode) throws AsyncSequenceRejectException {
      doReject(errorCode, null);
      throw new AsyncSequenceRejectException();
   }

   /**
    * Halts current thread, rejects the sequence of runnables with the given exception,
    * logs the exceptions, and will cause the next error runnable to be invoked, with
    * ERROR_UNKNOWN as the error and the given exception.
    */
   public synchronized void reject(Exception e) {
      doReject(e);
      throw new AsyncSequenceRejectException();
   }

   /**
    * Rejects the sequence of runnables with the given error, will cause the next error
    * runnable to be invoked.
    */
   protected synchronized void doReject(Exception e) {
      if (e instanceof AsyncSequenceRejectException) {
         // do nothing, reject handler has already been called
         return;
      } else if (e instanceof ErrorResponseException) {
         doReject(((ErrorResponseException) e).errorCode, null);
      } else {
         logger.error(e.getMessage(), e);
         doReject(CoreContract.ERROR_UNKNOWN, e);
      }
   }

   /**
    * Rejects the sequence of runnables with the given error & error code, will cause the
    * next error runnable to be invoked.
    */
   protected synchronized void doReject(int errorCode, Exception errorException) {
      for (ix++; ix < sequence.size(); ix++) {
         Object obj = sequence.get(ix);
         if (obj instanceof ErrRunnable) {
            try {
               ((ErrRunnable) obj).run(errorCode);
            } catch (Exception e) {
               doReject(e);
            }
            return;
         }
         if (obj instanceof ErrRunnableWithSeq) {
            try {
               ((ErrRunnableWithSeq) obj).run(this, errorCode);
            } catch (Exception e) {
               doReject(e);
            }
            return;
         }
         if (obj instanceof ErrRunnableWithException) {
            try {
               ((ErrRunnableWithException) obj).run(this, errorCode, errorException);
            } catch (Exception e) {
               doReject(e);
            }
            return;
         }
      }
      // saved in case we add a handler later
      this.errorCode = errorCode;
      this.errorException = errorException;
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
               ((NormalRunnable) obj).run(this);
            } catch (Exception e) {
               doReject(e);
            }
            return;
         }
      }
   }

   public Response respondAsync() {
      return Response.ASYNC;
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
            } catch (Exception e) {
               seq.doReject(e);
            }
         } else {
            seq.doReject(res.errorCode(), null);
         }
      }
   }

   public void putValue(String name, Object obj) {
      sequenceValues.put(name, obj);
   }

   public <T> T getValue(String name) {
      return Util.cast(sequenceValues.get(name));
   }

}
