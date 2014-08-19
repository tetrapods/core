define([ "knockout", "jquery", "bootbox", "toolbox", "protocol/server", "protocol/tetrapod", "protocol/core" ],
      function(ko, $, bootbox, toolbox, Server, Tetrapod, CoreProt) {
         return new App();

         function App() {
            var self = this;
            var server = new Server(Tetrapod, CoreProt);
            var Core = server.consts["Core.Core"];
            var token = null;
            var authtoken;
            var model;

            self.server = server;
            self.run = run;
            self.login = login;
            self.onLogout = onLogout;
            self.modalData = ko.observable({});
            self.name = window.location.hostname;
            self.isProd = self.name == "chatbox.com" || (self.name.indexOf(".prod.") > 0);  

            function run(clusterModel) {
               ko.bindingHandlers.stopBinding = {
                     init: function() {
                         return { controlsDescendantBindings: true };
                     }
               };
               model = clusterModel;
               server.commsLog = true;
               authtoken = toolbox.getCookie("auth-token");
               ko.applyBindings(model, $("#cluster-table")[0]);
               var array = $(".app-bind");
               for (var i = 0; i < array.length; i++) {
                  ko.applyBindings(self, array[i]);
               }
               connect();
            }

            function connect() {
               server.connect(window.location.hostname, window.location.protocol == 'https:').listen(onConnected,
                     onDisconnected);
            }

            function onConnected() {
               $('#disconnected-alertbox').hide();
               model.services.removeAll();
               server.send("Register", {
                  build : 0,
                  contractId : 0,
                  name : "Web-Admin",
                  token : token
               }, Core.DIRECT).handle(onRegistered);
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
                  if (authtoken != null && authtoken != "") {
                     server.send("AdminAuthorize", {
                        token : authtoken
                     }, Core.DIRECT).handle(onLogin);
                  } else {
                     onLogout();
                  }
               }
            }

            function login() {
               server.send("AdminLogin", {
                  email : $('#email').val(),
                  password : $('#password').val(),
               }, Core.DIRECT).handle(function(result) {
                  if (result.isError()) {
                     bootbox.alert('Login Failed');
                  }
                  onLogin(result);
               });
            }

            function onLogin(result) {
               if (!result.isError()) {
                  if (result.token) {
                     authtoken = result.token;
                     toolbox.setCookie("auth-token", authtoken);
                  }
                  $('#login-wrapper').hide();
                  $('#app-wrapper').show();
                  server.send("ServicesSubscribe", {}, Core.DIRECT).handle(server.logResponse);
               } else {
                  onLogout();
               }
            }

            function onLogout() {
               authtoken = null;
               toolbox.deleteCookie("auth-token");
               $('#login-wrapper').show();
               $('#app-wrapper').hide();
               model.services([]);
            }

            self.modalOldPassword = ko.observable();
            self.modalNewPassword = ko.observable();
            self.onShowEditPassword = function() {
               self.modalOldPassword('');
               self.modalNewPassword('');
               $('#set-password-modal').modal('show');
            };
            self.onEditPassword = function() {
               server.send("AdminChangePassword", {
                  token : authtoken,
                  oldPassword : self.modalOldPassword(),
                  newPassword : self.modalNewPassword()
               }, Core.DIRECT).handle(function(res) {
                  if (!res.isError()) {
                     bootbox.alert('Your password has been changed');
                  } else {
                     if (res.errorCode == server.consts["Tetrapod"].INVALID_PASSWORD) {
                        bootbox.alert('Error: Incorrect Password');
                     } else {
                        bootbox.alert('Error: Change Password Failed');
                     }
                  }
               });
            };

         }
      });