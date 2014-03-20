package io.tetrapod.core.rpc;

abstract public class Request extends Structure {

   public Response dispatch(ServiceAPI is, RequestContext ctx) {
      return is.genericRequest(this, ctx);
   }

   // Core request errors.  All requests may return these as errors.
   // All error numbers < 100 are reserved

   /**
    * Catch all error
    */
   @ERR
   public static final int ERROR_UNKNOWN             = 1;

   /**
    * No service exists to which to relay the request
    */
   @ERR
   public static final int ERROR_SERVICE_UNAVAILABLE = 2;

   /**
    * Request timed out without returning a response.
    */
   @ERR
   public static final int ERROR_TIMEOUT             = 3;

   /**
    * Unable to deserialize the request
    */
   @ERR
   public static final int ERROR_SERIALIZATION       = 4;

   /**
    * Protocol versions are not compatible
    */
   @ERR
   public static final int ERROR_PROTOCOL_MISMATCH   = 5;

   /**
    * Service exists and received request, but doen't know how to handle it
    */
   @ERR
   public static final int ERROR_UNKNOWN_REQUEST     = 6;

}
