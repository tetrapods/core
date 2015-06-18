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
      self.modalData = ko.observable({});
      self.name = window.location.hostname;
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
         model.hosts.clear();
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
         server.sendDirect("AdminLogin", {
            email: $('#email').val(),
            password: $('#password').val(),
         }, function(result) {
            if (result.isError()) {
               bootbox.alert('Login Failed');
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
            $('#login-wrapper').hide();
            $('#app-wrapper').show();
            server.sendDirect("ServicesSubscribe", {}, server.logResponse);
            server.sendDirect("AdminSubscribe", {
               adminToken: self.authtoken
            }, server.logResponse);
         } else {
            onLogout();
         }
      }

      function onLogout() {
         self.authtoken = null;
         toolbox.deleteCookie("auth-token");
         $('#login-wrapper').show();
         $('#app-wrapper').hide();
         model.services([]);
      }

   }
});
