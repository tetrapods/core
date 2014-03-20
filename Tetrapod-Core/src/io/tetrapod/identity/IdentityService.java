package io.tetrapod.identity;

import io.tetrapod.core.DefaultService;
import io.tetrapod.core.rpc.Response;
import io.tetrapod.core.utils.Properties;
import io.tetrapod.protocol.identity.*;

public class IdentityService extends DefaultService implements IdentityContract.API {
   
   @Override
   public void serviceInit(Properties props) {
      super.serviceInit(props);
      setContract(new IdentityContract());
      setPeerContracts(/* non-core services we talk to: eg:*/ /* new WalletContract(), new StorageContract() */ );
   }

   @Override
   public Response requestCreate(CreateRequest r) {
      return null;
   }

   @Override
   public Response requestInfo(InfoRequest r) {
      return null;
   }

   @Override
   public Response requestLogin(LoginRequest r) {
      return null;
   }

}
