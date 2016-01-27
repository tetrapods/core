define(["knockout", "jquery", "alert", "toolbox", "protocol/server",
        "protocol/tetrapod", "protocol/core", "protocol/web"], function(ko, $, Alert, toolbox, Server, Tetrapod, CoreProt, Web) {
   return new App();

   function App() {
      var self = this;
      var server = new Server(Tetrapod, CoreProt, Web);
      var token = null;
      var model;

      self.coreConsts = server.consts['Core'].Core;
      self.tetrapodConsts = server.consts['Tetrapod'];
      self.server = server;
      self.run = run;
      self.login = login;
      self.onLogout = onLogout;
      self.changePassword = changePassword;
      self.modalData = ko.observable({});
      self.name = window.location.hostname;
      self.email = ko.observable();
      self.accountId = ko.observable();
      self.alertResponse = alertResponse;
      self.isProd = self.name == "chatbox.com" || self.name == "pgx.chatbox.com" || (self.name.indexOf(".prod.") > 0);

      function run(clusterModel) {
         ko.bindingHandlers.stopBinding = {
            init: function() {
               return {
                  controlsDescendantBindings: true
               };
            }
         };
         model = clusterModel;
         server.commsLog = true;
         self.authtoken = toolbox.getCookie("auth-token");
         ko.applyBindings(model, $("#cluster-view")[0]);
         var array = $(".app-bind");
         for (var i = 0; i < array.length; i++) {
            ko.applyBindings(self, array[i]);
         }
         connect();
      }

      function connect() {
         server.connect(window.location.hostname, window.location.protocol == 'https:').listen(onConnected, onDisconnected);
      }

      function onConnected() {
         $('#disconnected-alertbox').hide();
         model.clear();
         server.sendDirect("Web.Register", {
            build: 0,
            contractId: 0,
            name: "Web-Admin",
            token: token
         }, onRegistered);
      }

      function onDisconnected() {
         $('#disconnected-alertbox').show();
         setTimeout(function() {
            connect();
         }, 1000);
      }

      function onRegistered(result) {
         if (!result.isError()) {
            token = result.token;
            if (self.authtoken != null && self.authtoken != "") {
               server.send("AdminAuthorize", {
                  token: self.authtoken
               }, onLogin);
            } else {
               onLogout();
            }
         }
      }

      function login() {
         var email = $('#email').val().trim();
         var pwd = $('#password').val();
         server.send("AdminLogin", {
            email: email,
            password: pwd
         }, function(result) {
            if (result.isError()) {
               Alert.error('Login Failed');
            } else {
               self.email(email);
            }
            onLogin(result);
         });
      }

      function onLogin(result) {
         if (!result.isError()) {
            if (result.token) {
               self.authtoken = result.token;
               toolbox.setCookie("auth-token", self.authtoken);
            }
            if (result.email) {
               self.email(result.email);
            }
            if (result.accountId) {
               self.accountId(result.accountId);
            }

            refreshLoginToken(function() {
               $('#login-wrapper').hide();
               $('#app-wrapper').show();
               server.send("ServicesSubscribe", {
                  adminToken: self.sessionToken
               }, server.logResponse);
               server.send("AdminSubscribe", {
                  adminToken: self.sessionToken
               }, server.logResponse);
               setInterval(refreshLoginToken, 60000 * 10); // refresh token every 10 minutes
            });

         } else {
            onLogout();
         }
      }

      function refreshLoginToken(callback) {
         server.send("AdminSessionToken", {
            accountId: self.accountId(),
            authToken: self.authtoken,
         }, function(result) {
            if (result.isError()) {
               onLogout(true);
            } else {
               self.sessionToken = result.sessionToken;
               if (callback)
                  callback();
            }
         });
      }

      function onLogout(keepToken) {
         if (!keepToken) {
            self.authtoken = null;
            toolbox.deleteCookie("auth-token");
         }
         $('#login-wrapper').show();
         $('#app-wrapper').hide();
         model.clear();
         self.email(null);
         self.sessionToken = null;
         self.accountId(0);
      }

      function changePassword() {
         model.users.changePassword();
      }

      function alertResponse(result, req) {
         if (result.isError()) {
            var err = server.getErrorStrings(result.errorCode);
            err = err ? (" " + err.join(" ")) : "";
            console.warn(err);
            Alert.error(err);
         }
      }

   }
});
