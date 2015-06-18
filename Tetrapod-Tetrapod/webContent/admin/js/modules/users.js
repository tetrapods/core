define(function(require) {
   var $ = require("jquery");
   var ko = require("knockout"); 
   var Alert = require("alert");

   return Users;

   function Users(app) {
      var self = this;

      self.users = ko.observableArray([]);

      self.modalOldPassword = ko.observable('');
      self.modalNewPassword = ko.observable('');
      self.addUserEmail = ko.observable('');
      self.addUserPassword = ko.observable('');

      self.addAdminUser = addAdminUser;
      self.onAddAdminUser = onAddAdminUser;
      self.onEditPassword = onEditPassword;
      self.findUser = findUser;

      function addAdminUser() {
         $('#add-user-modal').modal('show');
      }

      function onAddAdminUser() {
         var email = self.addUserEmail().trim();
         if (email && email.length > 0) {
            app.server.sendDirect("AdminCreate", {
               token: app.authtoken,
               email: email.trim(),
               password: email.trim(),
               rights: 0
            }, app.server.logResponse);
         }
      }

      function onEditPassword() {
         // TODO: apply to specific user, not just ourselves
         app.server.sendDirect("AdminChangePassword", {
            token: app.authtoken,
            oldPassword: self.modalOldPassword().trim(),
            newPassword: self.modalNewPassword().trim(),
         }, function(res) {
            if (!res.isError()) {
               Alert.info('Your password has been changed');
            } else {
               if (res.errorCode == app.server.consts["Tetrapod"].INVALID_PASSWORD) {
                  Alert.error('Error: Incorrect Password');
               } else {
                  Alert.error('Error: Change Password Failed');
               }
            }
         });
      }

      function findUser(accountId) {
         var arr = self.users();
         for (var i = 0; i < arr.length; i++) {
            if (arr[i].accountId == accountId) {
               return arr[i];
            }
         }
         return null;
      }

      app.server.addMessageHandler("AdminUserAdded", function(msg) {
         var u = self.findUser(msg.admin.accountId);
         if (u) {
            self.users.remove(u);
         }
         u = new User(msg.admin, self);
         self.users.push(u);
         self.users.sort(compareUsers);
      });

      app.server.addMessageHandler("AdminUserRemoved", function(msg) {
         var u = self.findUser(msg.key);
         if (u) {
            self.users.remove(u);
         }
      });

      function compareUsers(a, b) {
         return a.email.localeCompare(b.email);
      }

      function User(def, users) {
         var self = this;

         self.email = def.email;
         self.deleteUser = deleteUser;
         self.changePassword = changePassword;

         function deleteUser() {
            // TODO
         }

         function changePassword() {
            users.modalOldPassword('');
            users.modalNewPassword('');
            $('#set-password-modal').modal('show');
         }

      }

   }
});