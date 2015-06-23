define(function(require) {
   var $ = require("jquery");
   var ko = require("knockout");
   var Alert = require("alert");

   return Users;

   function Users(app) {
      var self = this;
      var CONSTS = $.extend({}, app.server.consts["Core"] || {}, app.server.consts["Core.Core"] || {}, app.server.consts["Tetrapod.Admin"] || {});

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
         var u = self.findUser(msg.accountId);
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
         self.canModifyRights = ko.pureComputed(canModifyRights);

         self.clusterRead = ko.observable();
         self.clusterWrite = ko.observable();
         self.userRead = ko.observable();
         self.userWrite = ko.observable();

         updateRights(def.rights);

         function updateRights(rights) {
            self.updatingRights = true;
            self.clusterRead(rights & CONSTS.RIGHTS_CLUSTER_READ);
            self.clusterWrite(rights & CONSTS.RIGHTS_CLUSTER_WRITE);
            self.userRead(rights & CONSTS.RIGHTS_USER_READ);
            self.userWrite(rights & CONSTS.RIGHTS_USER_WRITE);
            self.updatingRights = false;
         }

         function deleteUser() {
            Alert.confirm("Are you sure you want to delete '" + self.email + "'?", function() {
               app.server.sendDirect("AdminDelete", {
                  token: app.authtoken,
                  accountId: self.accountId
               }, function(res) {
                  if (res.isError()) {
                     Alert.error('Error: Delete Admin Failed');
                  }
               });
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

         // get the bitmask int from the checkbox observables
         function rights() {
            var r = 0;
            if (self.clusterRead()) {
               r |= CONSTS.RIGHTS_CLUSTER_READ
            }
            if (self.clusterWrite()) {
               r |= CONSTS.RIGHTS_CLUSTER_WRITE
            }
            if (self.userRead()) {
               r |= CONSTS.RIGHTS_USER_READ
            }
            if (self.userWrite()) {
               r |= CONSTS.RIGHTS_USER_WRITE
            }
            return r;
         }

         self.clusterRead.subscribe(changeRights);
         self.clusterWrite.subscribe(changeRights);
         self.userRead.subscribe(changeRights);
         self.userWrite.subscribe(changeRights);

         function changeRights() {
            if (!self.updatingRights) {
               var r = rights();
               app.server.sendDirect("AdminChangeRights", {
                  token: app.authtoken,
                  accountId: self.accountId,
                  rights: r
               }, function(res) {
                  if (res.isError()) {
                     Alert.error('Error: Update Rights Failed');
                     r = def.rights;
                  }
                  def.rights = r;
                  updateRights(r);
               });
            }
         }

         function canModifyRights() {
            return app.accountId() != self.accountId;
         }

      }

   }
});