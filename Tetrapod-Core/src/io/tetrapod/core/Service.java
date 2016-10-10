package io.tetrapod.core;

import java.util.Map;

import io.tetrapod.core.rpc.ServiceAPI;
import io.tetrapod.core.tasks.Task;
import io.tetrapod.protocol.core.ServerAddress;

public interface Service extends Session.Helper, ServiceAPI {

   void startNetwork(ServerAddress server, String token, Map<String, String> opts, Launcher launcher) throws Exception;
   Task<Void> readyToServe();
}
