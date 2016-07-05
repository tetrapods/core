define(["knockout", "jquery", "alert", "toolbox", "protocol/server", "protocol/tetrapod", "protocol/core", "protocol/web"], function(ko, $, Alert, toolbox, Server, Tetrapod, CoreProt, Web) {
   return new App();

   function App() {
      var self = this;
      var server = new Server(Tetrapod, CoreProt, Web);
      var token = null;
      var model;

      self.coreConsts = server.consts['Core'].Core;
      self.coreConsts.Admin = server.consts['Core'].Admin;
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
      self.isProd = self.name == "orgs.chatbox.com" || self.name == "pgx.chatbox.com" || (self.name.indexOf(".prod.") > 0);
      self.sendTo = sendTo;
      self.sendAny = sendAny;
      self.sendDirect = sendDirect;

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
         ko.applyBindings(model, $("#app-wrapper")[0]);
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
               server.send("AdminSubscribe", {
                  accountId: self.accountId(),
                  authToken: self.sessionToken
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
         if (keepToken != true) {
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

      function addArgs(args) {
         if (args._exactArgs) {
            args._exactArgs = undefined;
            return;
         }
         if (!args.hasOwnProperty("accountId"))
            args.accountId = self.accountId();
         if (!args.hasOwnProperty("authToken"))
            args.authToken = self.sessionToken;
      }

      function sendTo(reqName, args, toEntityId, callback) {
         addArgs(args)
         self.server.sendTo(reqName, args, toEntityId, callback);
      }

      function sendAny(reqName, args, callback) {
         addArgs(args)
         self.server.send(reqName, args, callback);
      }

      function sendDirect(reqName, args, callback) {
         addArgs(args)
         self.server.sendDirect(reqName, args, callback);
      }

   }
});
