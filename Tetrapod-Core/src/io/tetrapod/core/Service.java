package io.tetrapod.core;

import io.tetrapod.core.rpc.*;

public interface Service extends Session.Helper, ServiceAPI {

   void startNetwork(String hostAndPort, String token) throws Exception;

}
