define(function(require) {
   var $ = require("jquery");
   var ko = require("knockout");
   var Alert = require("alert");

   return Users;

   function Users(app) {
      var self = this;

      self.app = app;
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
            app.sendAny("AdminCreate", {
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
         app.sendAny("AdminChangePassword", {
            oldPassword: self.modalOldPassword().trim(),
            newPassword: self.modalNewPassword().trim(),
         }, function(res) {
            if (!res.isError()) {
               Alert.info('Your password has been changed');
            } else {
               if (res.errorCode == app.tetrapodConsts.error.INVALID_CREDENTIALS) {
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
         self.app1 = ko.observable();
         self.app2 = ko.observable();
         self.app3 = ko.observable();
         self.app4 = ko.observable();
 
         
         updateRights(def.rights);

         function updateRights(rights) {
            self.updatingRights = true;            
            self.clusterRead(rights & app.coreConsts.Admin.RIGHTS_CLUSTER_READ);
            self.clusterWrite(rights & app.coreConsts.Admin.RIGHTS_CLUSTER_WRITE);
            self.userRead(rights & app.coreConsts.Admin.RIGHTS_USER_READ);
            self.userWrite(rights & app.coreConsts.Admin.RIGHTS_USER_WRITE);
            self.app1(rights & app.coreConsts.Admin.RIGHTS_APP_SET_1);
            self.app2(rights & app.coreConsts.Admin.RIGHTS_APP_SET_2);
            self.app3(rights & app.coreConsts.Admin.RIGHTS_APP_SET_3);
            self.app4(rights & app.coreConsts.Admin.RIGHTS_APP_SET_4);
            self.updatingRights = false;
         }

         function deleteUser() {
            Alert.confirm("Are you sure you want to delete '" + self.email + "'?", function() {
               app.sendAny("AdminDelete", {
                  targetAccountId: self.accountId
               }, function(res) {
                  if (res.isError()) {
                     Alert.error('Error: Delete Admin Failed');
                  }
               });
            });
         }

         function resetPassword() {
            Alert.prompt("Change password for '" + self.email + "':", function(val) {
               app.sendAny("AdminResetPassword", {
                  targetAccountId: self.accountId,
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
               r |= app.coreConsts.Admin.RIGHTS_CLUSTER_READ
            }
            if (self.clusterWrite()) {
               r |= app.coreConsts.Admin.RIGHTS_CLUSTER_WRITE
            }
            if (self.userRead()) {
               r |= app.coreConsts.Admin.RIGHTS_USER_READ
            }
            if (self.userWrite()) {
               r |= app.coreConsts.Admin.RIGHTS_USER_WRITE
            }
            if (self.app1()) {
               r |= app.coreConsts.Admin.RIGHTS_APP_SET_1
            }
            if (self.app2()) {
               r |= app.coreConsts.Admin.RIGHTS_APP_SET_2
            }
            if (self.app3()) {
               r |= app.coreConsts.Admin.RIGHTS_APP_SET_3
            }
            if (self.app4()) {
               r |= app.coreConsts.Admin.RIGHTS_APP_SET_4
            }
            return r;
         }

         self.clusterRead.subscribe(changeRights);
         self.clusterWrite.subscribe(changeRights);
         self.userRead.subscribe(changeRights);
         self.userWrite.subscribe(changeRights);
         self.app1.subscribe(changeRights);
         self.app2.subscribe(changeRights);
         self.app3.subscribe(changeRights);
         self.app4.subscribe(changeRights);

         function changeRights() {
            if (!self.updatingRights) {
               var r = rights();
               app.sendAny("AdminChangeRights", { 
                  targetAccountId: self.accountId,
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
