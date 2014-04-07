package io.tetrapod.core;

import io.tetrapod.core.DefaultService;
import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.*;
import io.tetrapod.protocol.identity.*;
import io.tetrapod.protocol.service.*;

import org.slf4j.*;

public class TestService extends DefaultService implements BaseServiceContract.API {
   public static final Logger logger = LoggerFactory.getLogger(TestService.class);

   public TestService() {
      setMainContract(new IdentityContract());
      addPeerContracts(/* non-core services we talk to: eg:*//* new WalletContract(), new StorageContract() */);
   }

   @Override
   public void onRegistered() {

      updateStatus(status & ~Core.STATUS_STARTING);
   }

   @Override
   public void onShutdown(boolean restarting) {}

   @Override
   public Response requestServiceIcon(ServiceIconRequest r, RequestContext ctx) {
      return new ServiceIconResponse("media/identity.png");
   }
}
