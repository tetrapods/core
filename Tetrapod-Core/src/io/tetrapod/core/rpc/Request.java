package io.tetrapod.core.rpc;


abstract public class Request extends Structure {
   
   public Response dispatch(ServiceAPI is) {
      return is.genericRequest(this);
   }
   
}
