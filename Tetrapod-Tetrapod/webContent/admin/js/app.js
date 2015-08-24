define(["knockout", "jquery", "bootbox", "toolbox", "protocol/server", "protocol/tetrapod", "protocol/core"], function(ko, $, bootbox, toolbox, Server, Tetrapod, CoreProt) {
   return new App();

   function App() {
      var self = this;
      var server = new Server(Tetrapod, CoreProt);
      var Core = server.consts["Core.Core"];
      var token = null;
      var model;

      self.leaderEntityId;
      self.server = server;
      self.run = run;
      self.login = login;
      self.onLogout = onLogout;
      self.changePassword = changePassword;
      self.modalData = ko.observable({});
      self.name = window.location.hostname;
      self.email = ko.observable();
      self.accountId = ko.observable();
      self.isProd = self.name == "chatbox.com" || self.name == "xbox.chatbox.com" || (self.name.indexOf(".prod.") > 0);

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
         server.sendDirect("Register", {
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
               server.sendDirect("AdminAuthorize", {
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
         server.sendDirect("AdminLogin", {
            email: email,
            password: pwd
         }, function(result) {
            if (result.isError()) {
               bootbox.alert('Login Failed');
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
               server.sendDirect("ServicesSubscribe", {
                  adminToken: self.sessionToken
               }, server.logResponse);
               server.sendDirect("AdminSubscribe", {
                  adminToken: self.sessionToken
               }, server.logResponse);
               setInterval(refreshLoginToken, 60000 * 10); // refresh token every 10 minutes
            });

         } else {
            onLogout();
         }
      }

      function refreshLoginToken(callback) {
         server.sendDirect("AdminSessionToken", {
            accountId: self.accountId(),
            authToken: self.authtoken,
         }, function(result) {
            if (result.isError()) {
               onLogout(true);
            } else if (callback) {
               self.sessionToken = result.sessionToken;
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
   }
});
