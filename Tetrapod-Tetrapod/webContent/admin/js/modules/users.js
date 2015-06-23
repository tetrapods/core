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

      self.changePassword = changePassword;
      self.addAdminUser = addAdminUser;
      self.onAddAdminUser = onAddAdminUser;
      self.onEditPassword = onEditPassword;
      self.findUser = findUser;
      self.clear = clear;

      function addAdminUser() {
         $('#add-user-modal').modal('show');
      }

      function onAddAdminUser() {
         var email = self.addUserEmail().trim();
         if (email && email.length > 0) {
            app.server.sendDirect("AdminCreate", {
               token: app.authtoken,
               email: email,
               password: email,
               rights: 0
            }, function(res) {
               if (!res.isError()) {
                  Alert.info('New admin account created with initial password same as "' + email + '"');
               } else {
                  Alert.error('Error: Create Admin User failed: ' + app.server.getErrorStrings(res.errorCode));
               }
            });
         } else {
            Alert.error("Email must not be empty");
         }
      }

      function clear() {
         self.users.removeAll();
      }

      // shows the change password modal
      function changePassword() {
         self.modalOldPassword('');
         self.modalNewPassword('');
         $('#set-password-modal').modal('show');
      }

      // called when change password dialog is submitted
      function onEditPassword() {
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
         self.accountId = def.accountId;
         self.deleteUser = deleteUser;
         self.resetPassword = resetPassword;

         function deleteUser() {
            Alert.confirm("Are you sure you want to delete '" + self.email + "'?", function() {
               Alert.error("Implement Me!"); // TODO
            });
         }

         function resetPassword() {
            Alert.prompt("Change password for '" + self.email + "':", function(val) {
               app.server.sendDirect("AdminResetPassword", {
                  token: app.authtoken,
                  accountId: self.accountId,
                  password: val
               }, function(res) {
                  if (res.isError()) {
                     Alert.error('Error: Reset Password Failed');
                  } else {
                     Alert.info('Password has been changed.');
                  }
               });
            });
         }

      }

   }
});