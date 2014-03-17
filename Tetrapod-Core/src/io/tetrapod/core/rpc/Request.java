package io.tetrapod.core.rpc;


abstract public class Request extends Structure {
   
   public static enum Security {
      PUBLIC,
      PROTECTED,
      INTERNAL,
      ADMIN
   }
   
   public Response dispatch(ServiceAPI is) {
      return is.genericRequest(this);
   }
   
}
