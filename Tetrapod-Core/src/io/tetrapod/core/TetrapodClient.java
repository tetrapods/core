package io.tetrapod.core;

import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.*;

import java.util.List;

import org.slf4j.*;

/**
 * A base class for implementing a client or bot
 */
public class TetrapodClient implements SessionFactory, Session.Helper {

   public static final Logger    logger          = LoggerFactory.getLogger(TetrapodClient.class);

   private final Dispatcher      dispatcher      = new Dispatcher();
   private final MessageHandlers messageHandlers = new MessageHandlers();
   private final Client          client;

   private String                token;

   public TetrapodClient() {
      addContracts(new TetrapodContract());
      client = new Client(this);
   }

   public void connect(String host, int port) throws Exception {
      client.connect(host, port, dispatcher).sync();
   }

   public void addContracts(Contract... contracts) {
      for (Contract c : contracts) {
         c.registerStructs();
      }
   }

   public void addSubscriptionHandler(Contract sub, SubscriptionAPI handler) {
      messageHandlers.add(sub, handler);
   }

   public void addMessageHandler(Message k, SubscriptionAPI handler) {
      messageHandlers.add(k, handler);
   }

   /**
    * Session factory for our session to our parent TetrapodService
    */
   @Override
   public Session makeSession(SocketChannel ch) {
      final Session ses = new WireSession(ch, this);
      ses.setMyEntityType(Core.TYPE_CLIENT);
      ses.addSessionListener(new Session.Listener() {
         @Override
         public void onSessionStop(Session ses) {
            // TODO: reconnect
         }

         @Override
         public void onSessionStart(Session ses) {
            register();
         }
      });
      return ses;
   }

   @Override
   public Dispatcher getDispatcher() {
      return dispatcher;
   }

   @Override
   public ServiceAPI getServiceHandler(int contractId) {
      return null; // N/A
   }

   @Override
   public int getContractId() {
      return 0; // N/A
   }

   @Override
   public List<SubscriptionAPI> getMessageHandlers(int contractId, int structId) {
      return messageHandlers.get(contractId, structId);
   }

   private void register() {
      sendRequest(new RegisterRequest(0, token, getContractId(), getClientName(), 0), Core.UNADDRESSED).handle(new ResponseHandler() {
         @Override
         public void onResponse(Response res) {
            if (res.isError()) {
               logger.error("Unable to register {}", res.errorCode());
            } else {
               RegisterResponse r = (RegisterResponse) res;
               TetrapodClient.this.token = r.token;
               logger.info(String.format("%s My entityId is 0x%08X", client.getSession(), r.entityId));
               client.getSession().setMyEntityId(r.entityId);
               client.getSession().setTheirEntityId(r.parentId);
               client.getSession().setMyEntityType(Core.TYPE_CLIENT);
               client.getSession().setTheirEntityType(Core.TYPE_TETRAPOD);
               onRegistered();
            }
         }
      });
   }

   public boolean isConnected() {
      return client.isConnected();
   }

   public Async sendRequest(Request req, int toEntityId) {
      return client.getSession().sendRequest(req, toEntityId, (byte) 30);
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   /**
    * Subclasses can override this
    */
   protected void onRegistered() {}

   /**
    * Subclasses can override this
    */
   public String getClientName() {
      return "Client";
   }

}
