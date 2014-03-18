package io.tetrapod.core;

import org.slf4j.*;

import io.tetrapod.core.protocol.*;
import io.tetrapod.core.registry.Registry;
import io.tetrapod.core.rpc.*;

public class TetrapodService implements TetrapodContract.API {
   public static final Logger logger = LoggerFactory.getLogger(TetrapodService.class);

   public final Dispatcher    dispatcher;
   public final Registry      registry;
   public final Server        privateServer;
   public final Server        publicServer;

   // TODO: Configuration
   public TetrapodService() {
      dispatcher = new Dispatcher();
      registry = new Registry(1); // FIXME -- each service needs to be issued a unique id
      publicServer = new Server(9800, dispatcher);
      privateServer = new Server(9900, dispatcher);

   }

   public void start() {
      try {
         privateServer.start();
         publicServer.start();
      } catch (Exception e) {
         // FIXME: fail service
         logger.error(e.getMessage(), e);
      }
   }

   @Override
   public Response requestRegister(RegisterRequest r) {
      registry.register(null); // TODO
      return new RegisterResponse(/* FIXME: Code generate constructors */);
   }

   @Override
   public Response genericRequest(Request r) {
      return null;
   }

}
