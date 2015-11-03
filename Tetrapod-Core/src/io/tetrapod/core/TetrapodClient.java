package io.tetrapod.core;

import io.netty.channel.socket.SocketChannel;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base class for implementing a client or bot
 */
public class TetrapodClient implements SessionFactory, Session.Helper {

   /**
    * FIXME -- only used for test bots right now, but this should be fixed if we ever need for serious use
    */
   private static final TrustManager DUMMY_TRUST_MANAGER = new X509TrustManager() {
                                                            public X509Certificate[] getAcceptedIssuers() {
                                                               return new X509Certificate[0];
                                                            }

                                                            public void checkClientTrusted(X509Certificate[] chain, String authType)
                                                                     throws CertificateException {}

                                                            public void checkServerTrusted(X509Certificate[] chain, String authType)
                                                                     throws CertificateException {}
                                                         };
   public static final Logger        logger              = LoggerFactory.getLogger(TetrapodClient.class);

   private final Dispatcher          dispatcher;
   private final MessageHandlers     messageHandlers     = new MessageHandlers();
   private final Client              client;
   private final ServerAddress       address;
   private final boolean             ssl;
   private String                    token;

   public TetrapodClient(Dispatcher dispatcher, String host, int port, boolean ssl) {
      this.dispatcher = dispatcher;
      this.address = new ServerAddress(host, port);
      this.ssl = ssl;
      addContracts(new TetrapodContract());
      client = new Client(this);
   }

   public void connect() throws Exception {
      if (ssl) {
         final SSLContext ctx = SSLContext.getInstance("TLS");
         ctx.init(null, new TrustManager[] { DUMMY_TRUST_MANAGER }, null);

         client.enableTLS(ctx);
      }
      client.connect(address.host, address.port, dispatcher).sync();
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
            scheduleReconnect(1);
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
   public int getContractId() {
      return 0; // N/A
   }

   public void disconnect() {
      client.close();
   }

   @Override
   public List<SubscriptionAPI> getMessageHandlers(int contractId, int structId) {
      return messageHandlers.get(contractId, structId);
   }

   private void register() {
      sendDirectRequest(new RegisterRequest(token, getContractId(), getClientName(), 0, Util.getHostName(), "build")).handle(res -> {
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
      });
   }

   public boolean isConnected() {
      return client.isConnected();
   }

   public Async sendDirectRequest(Request req) {
      return client.getSession().sendRequest(req, Core.DIRECT, (byte) 30);
   }

   public Async sendRequest(Request req, int toEntityId) {
      return client.getSession().sendRequest(req, toEntityId, (byte) 30);
   }

   public int getParentId() {
      return client.getSession().getTheirEntityId();
   }

   public Response sendPendingRequest(Request req, int toEntityId, PendingResponseHandler handler) {
      return client.getSession().sendPendingRequest(req, toEntityId, (byte) 30, handler);
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

   private void scheduleReconnect(final int seconds) {
      dispatcher.dispatch(seconds, TimeUnit.SECONDS, () -> {
         try {
            if (!isConnected()) {
               logger.debug("Reconnecting");
               connect();
            }
         } catch (Exception e) {
            logger.error(e.getMessage(), e);
            scheduleReconnect(seconds * 2);
         }
      });
   }

   @Override
   public Async dispatchRequest(RequestHeader header, Request req, Session fromSession) {
      return null; // clients don't handle requests
   }

}
