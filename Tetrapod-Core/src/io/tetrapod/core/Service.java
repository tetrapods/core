package io.tetrapod.core;

import io.tetrapod.core.rpc.ServiceAPI;
import io.tetrapod.protocol.core.ServerAddress;

public interface Service extends Session.Helper, ServiceAPI {

   void startNetwork(ServerAddress server, String token) throws Exception;

}
