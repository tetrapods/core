package io.tetrapod.identity;

import io.tetrapod.core.DefaultService;
import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.identity.*;

public class IdentityService extends DefaultService implements IdentityContract.API {

   public void onRegistered() {
      addContracts(new IdentityContract());
      addPeerContracts(/* non-core services we talk to: eg:*//* new WalletContract(), new StorageContract() */);
   }

   @Override
   public Response requestCreate(CreateRequest r, RequestContext ctx) {
      return null;
   }

   @Override
   public Response requestInfo(InfoRequest r, RequestContext ctx) {
      return null;
   }

   @Override
   public Response requestLogin(LoginRequest r, RequestContext ctx) {
      return null;
   }

}
