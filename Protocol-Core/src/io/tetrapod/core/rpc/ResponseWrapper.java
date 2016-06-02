package io.tetrapod.core.rpc;

import com.sun.javaws.exceptions.ErrorCodeResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author paulm
 *         Created: 6/2/16
 */
public class ResponseWrapper<TResp extends Response> {
   private static final Logger logger = LoggerFactory.getLogger(ResponseWrapper.class);

   private final Integer errorCode;
   private final TResp response;

   protected ResponseWrapper(TResp response) {
      this.response = response;
      this.errorCode = null;
   }

   protected ResponseWrapper(Integer errorCode) {
      this.errorCode = errorCode;
      this.response = null;
   }

   public boolean isError() {
      return errorCode != null;
   }

   public Integer getErrorCode() {
      return errorCode;
   }

   public TResp getResponse() {
      if (errorCode != null) {
         throw new ErrorResponseException(errorCode);
      }
      return response;
   }
}
