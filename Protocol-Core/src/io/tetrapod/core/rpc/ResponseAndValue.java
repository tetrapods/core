package io.tetrapod.core.rpc;

/**
 * @author paulm
 *         Created: 6/21/16
 */
public class ResponseAndValue<TResp extends Response, TValue> {
   private final TValue value;
   private final TResp response;

   public ResponseAndValue(TResp response, TValue value) {
      this.value = value;
      this.response = response;
   }

   public TValue getValue() {
      return value;
   }

   public TResp getResponse() {
      return response;
   }
}
