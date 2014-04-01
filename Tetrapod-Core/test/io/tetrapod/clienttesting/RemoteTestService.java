package io.tetrapod.clienttesting;

import java.util.concurrent.TimeUnit;

import io.tetrapod.core.DefaultService;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.rpc.Error;
import io.tetrapod.protocol.clienttesting.*;
import io.tetrapod.protocol.core.*;

import org.slf4j.*;

public class RemoteTestService extends DefaultService implements RemoteTestContract.API {
   private static final Logger logger = LoggerFactory.getLogger(RemoteTestService.class);
   private DatatypeTestMessage testMessage;
   private int                 testTopicId;

   public RemoteTestService() {
      setMainContract(new RemoteTestContract());
      testMessage = new DatatypeTestMessage();
      testMessage.unicodeNormalization = "e\u0301 should look like \u00E9";
      testMessage.unicodeOutsideBMP = "Earth:\u0001F30D Sun:\u0001F31E";
   }

   @Override
   public void onRegistered() {
      sendRequest(new PublishRequest(), getParentId()).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            if (res.isError()) {
               fail("Can't publish topic", res.errorCode());
            } else {
               PublishResponse r = (PublishResponse) res;
               testTopicId = r.topicId;
               scheduleRepeatedBroadcast();
            }
         }
      });
   }

   @Override
   public void onShutdown(boolean restarting) {
      // TODO Auto-generated method stub

   }

   private void scheduleRepeatedBroadcast() {
      dispatcher.dispatch(3, TimeUnit.SECONDS, new Runnable() {
         public void run() {
            if (dispatcher.isRunning()) {
               try {
                  logger.trace("Sending Test Broadcast on {}", testTopicId);
                  sendDatatypeTest();
               } catch (Throwable e) {
                  logger.error(e.getMessage(), e);
               }
               scheduleRepeatedBroadcast();
            }
         }
      });
   }

   private void sendDatatypeTest() {
      sendBroadcastMessage(testMessage, testTopicId);
   }

   @Override
   public Response requestMeaningOfLifeTheUniversAndEverything(MeaningOfLifeTheUniversAndEverythingRequest r, RequestContext ctx) {
      if (r.guess > 0) {
         return new MeaningOfLifeTheUniversAndEverythingResponse();
      } else {
         return new Error(RemoteTestContract.ERROR_MISSING_GUESS);
      }
   }

}
